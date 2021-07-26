extern crate ndk;
extern crate futures;
extern crate jni;

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JValue};
use ndk::bitmap::{AndroidBitmapInfo, AndroidBitmap, BitmapResult, BitmapFormat};
use std::ffi::c_void;
use rand::Rng;

#[macro_use]
extern crate log;
extern crate android_logger;

use android_logger::{Config, FilterBuilder};
use log::Level;
use jni::sys::{jint, jobject, jsize};
use jni::signature::JavaType;


#[cfg_attr(target_os = "android", )]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_white_dominantColor_DominantColors_kmeans(
    env: JNIEnv,
    _class: JClass,
    bitmap: jobject,
    numColor: jint,
) -> jobject {
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Debug)
            .with_tag("dominantColor")
        // .with_filter(
        //     // configure messages for specific crate
        //     FilterBuilder::new()
        //         .parse("debug,hello::crate=error")
        //         .build()
        // )
    );

    info!("numColor is {:?}", numColor);

    let num: i32 = numColor as i32;
    if num < 0 {
        //num不合法
        info!("numColor is illegal,num < 0");
        return JObject::null().into_inner();
    }

    // let inner = bitmap.into_inner();
    //check bitmap
    let aBitmap: AndroidBitmap =
        unsafe { AndroidBitmap::from_jni(env.get_native_interface(), bitmap) };

    let infoResult: BitmapResult<AndroidBitmapInfo> = aBitmap.get_info();

    let bmpInfo: AndroidBitmapInfo;
    if infoResult.is_err() {
        info!("bitmap is illegal");
        return JObject::null().into_inner();
    } else {
        bmpInfo = infoResult.unwrap();
        if bmpInfo.format() != BitmapFormat::RGBA_8888 {
            info!("bitmap format must be RGBA_8888");
            return JObject::null().into_inner();
        }

        info!("bitmap info {:?}", bmpInfo);
    }

    // try lock pixels
    let lockResult = aBitmap.lock_pixels();
    if lockResult.is_err() {
        info!("lock_pixels fail.");
        //lock_pixels fail,return
        return JObject::null().into_inner();
    }

    let piexls = lockResult.unwrap();

    info!("-- kmeans start from rust --");
    let (originCentroids, precentages) = kmeans(bmpInfo, piexls, num as usize);

    let mut centroids = vec![0; num as usize];
    for i in 0..num as usize {
        centroids[i] = rev_color(originCentroids[i]);
    }

    // unlock pixels
    aBitmap.unlock_pixels();

    info!("kmeans over,centroids: {:?},precentages: {:?}", centroids, precentages);
    let result = make_result(&env, centroids, precentages);
    return result;
}

#[derive(Copy, Clone, Debug)]
pub struct ColorSum {
    r: f64,
    g: f64,
    b: f64,
}


/**
 * kmeans的真正方法
 * @param info 传入的Android bitmap的信息
 * @param pixels bitmap的像素信息
 * @param num_colors kmeans的k值，即最后得到的集合的大小
 * @param centroids 质心数组。也就是最后得到的结果数组
 * @param precentages 得到的结果百分比数组。和centroids下标保持一致
 */
fn kmeans(
    info: AndroidBitmapInfo,
    pixels: *mut c_void,
    num_colors: usize,
) -> (Vec<u32>, Vec<f64>) {
    //保存一下指针开始的位置
    let start = pixels.clone();

    let mut centroids: Vec<u32> = vec![0; num_colors as usize];

    let mut filled: u32 = 0;
    let mut re_gen_count: u8 = 0;
    while filled < num_colors as u32 {
        //用rand方法产生随机种子坐标，从图片中取出这些种子坐标对应的数据。种子的数量由numColors决定
        let xx = rand::thread_rng().gen_range(0..info.width());
        let yy = rand::thread_rng().gen_range(0..info.height());

        // debug!("kmeans xx: {:?}, yy: {:?} ,info stride: {:?}", xx, yy, info.stride());
        let offset = yy * info.stride() + xx * 4;
        //用u32来表示颜色
        let new_color: u32 = unsafe {
            //每个x需要移动4个byte，也就是32个bit
            let new_ptr = pixels.offset(offset as isize);
            // debug!("kmeans offset: {:?} new_ptr: {:?}", offset, new_ptr);
            *(new_ptr as *mut u32)
        };

        // debug!("kmeans new_color: {:?}", new_color);

        let mut contained = centroids.contains(&new_color);

        //如果随机的颜色和之前就一样，则舍弃此次的随机，重新取色
        //但是这样有一个问题：如果图片就是一张纯色图，则陷入死循环。
        //加入一个机制，如果重试了三次，颜色还是和之前的一样，则放弃随机。
        if !contained || re_gen_count > 3 {
            centroids[filled as usize] = new_color;
            filled += 1;
            re_gen_count = 0;
        } else {
            re_gen_count += 1;
        }
    }
    debug!("kmeans random centroids: {:?}", centroids);

    let mut sums: Vec<ColorSum> = vec![ColorSum { r: 0.0, g: 0.0, b: 0.0 }; num_colors];
    let mut members: Vec<u32> = vec![0; num_colors];

    let mut line = start;
    let mut index = 0;
    let mut max_error: f64 = 0.0;


    while index < 100 {
        for i in 0..num_colors {
            sums[i] = ColorSum { r: 0.0, g: 0.0, b: 0.0 };
            members[i] = 0;
        }
        line = start;

        // debug!("kmeans try,index: {:?}", index);

        for y in 0..info.height() {
            for x in 0..info.width() {
                let mut min_dist_sq = f64::MAX;
                let mut best_centroid_num: usize = 0;
                //当前点和质心点的距离（质心点一开始是随机值，后续随着算法迭代被逐步替换）
                let try_centroid = unsafe {
                    let offset = line.offset((x * 4) as isize);
                    &mut *(offset as *mut u32)
                };
                for num in 0..num_colors {
                    let old_centroids = centroids.get(num).unwrap();

                    let dist_sq = distance_sq(*try_centroid, *old_centroids);
                    // debug!("kmeans try_centroid: {:?},old_centroids: {:?},dist_sq: {:?}", try_centroid, old_centroids, dist_sq);

                    if dist_sq < min_dist_sq {
                        min_dist_sq = dist_sq;
                        best_centroid_num = num as usize
                    }
                }

                //当前这个xy定位的颜色聚合进去了。
                sums[best_centroid_num].r += red(*try_centroid) as f64;
                sums[best_centroid_num].g += green(*try_centroid) as f64;
                sums[best_centroid_num].b += blue(*try_centroid) as f64;
                members[best_centroid_num] += 1;
            }
            unsafe {
                line = line.offset(info.stride() as isize);
            }
        }

        debug!("kmeans sums: {:?}", sums);

        // debug!("kmeans try,x y loop over");

        max_error = 0.0;
        for num in 0..num_colors {
            let new_centroid: u32;
            let member = members.get(num).unwrap();
            if *member == 0 {
                new_centroid = 0xFFFFFFFF;
            } else {
                let color: &ColorSum = &sums[num];
                new_centroid = cal_color((color.r / *member as f64) as u32,
                                         (color.g / *member as f64) as u32,
                                         (color.b / *member as f64) as u32, );
            }
            let old_centroids = centroids.get(num).unwrap();
            let dist_sq = distance_sq(new_centroid, *old_centroids);

            debug!("kmeans old_centroids:{:?},new_centroid: {:?} dist_sq: {:?}", *old_centroids, new_centroid, dist_sq);

            if dist_sq > max_error {
                max_error = dist_sq;
            }
            centroids[num] = new_centroid;
        }

        // debug!("kmeans try, index :{:?}, maxError {:?}", index, max_error);

        if max_error <= 1.0 {
            break;
        }
        index += 1;
    };

    // info!("kmeans centroids: {:?}", centroids);
    // info!("kmeans members: {:?}", members);
    let total = info.width() * info.height();
    let mut precentages: Vec<f64> = Vec::new();
    for num in 0..num_colors {
        let member = members[num];
        let precentage = member as f64 / total as f64;
        precentages.push(precentage)
    }
    info!("kmeans precentages: {:?}", precentages);

    return (centroids, precentages);
}

pub const ALPHA_MASK: u32 = 0xFF000000;
pub const BLUE_MASK: u32 = 0x00FF0000;
pub const BLUE_SHIFT: u32 = 16;
pub const GREEN_MASK: u32 = 0x0000FF00;
pub const GREEN_SHIFT: u32 = 8;
pub const RED_MASK: u32 = 0x000000FF;

/**
 * 计算两个颜色的距离。（0.3、0.59、0.11是经验值？）
 * @param c1
 * @param c2
 * @return
 */
fn distance_sq(c1: u32, c2: u32) -> f64 {
    let red = (red(c1) as i64 - red(c2) as i64) as f64 * 0.30;
    let green = (green(c1) as i64 - green(c2) as i64) as f64 * 0.59;
    let blue = (blue(c1) as i64 - blue(c2) as i64) as f64 * 0.11;

    return red * red +
        green * green +
        blue * blue;
}


fn red(color: u32) -> u32 {
    color & RED_MASK
}

fn green(color: u32) -> u32 {
    (color & GREEN_MASK) >> GREEN_SHIFT
}

fn blue(color: u32) -> u32 {
    (color & BLUE_MASK) >> BLUE_SHIFT
}

fn cal_color(r: u32, g: u32, b: u32) -> u32 {
    return ALPHA_MASK
        | ((b << BLUE_SHIFT) & BLUE_MASK)
        | ((g << GREEN_SHIFT) & GREEN_MASK)
        | (r & RED_MASK);
}

fn rev_color(c: u32) -> u32 {
    return cal_color(blue(c), green(c), red(c));
}

//get an empty result
fn make_result(env: &JNIEnv,
               centroids: Vec<u32>,
               precentages: Vec<f64>)
               -> jobject {
    let domincolor_result = env.find_class("com/white/dominantColor/DominantColor");
    // if we can't found DominantColor class in java, just crash it!
    let domincolor_class = domincolor_result.unwrap();

    // let color_id = env.get_field_id(domincolor_class, "color", "I").unwrap();
    // let percentage_id = env.get_field_id(domincolor_class, "percentage", "F").unwrap();
    // let init_method = env.get_method_id(domincolor_class, "<init>", "()V").unwrap();

    let array = env.new_object_array(centroids.len() as i32,
                                     domincolor_class,
                                     (::std::ptr::null_mut() as jobject)).unwrap();

    for i in 0..centroids.len() as usize {
        let centroid = centroids[i];
        let precentage = precentages[i];

        // let dominant_color_obj = env.new_object(domincolor_class, "<init>", &[]).unwrap().into_inner();
        let dominant_color_obj_r =
            env.new_object("com/white/dominantColor/DominantColor", "()V", &[]);

        if dominant_color_obj_r.is_err() {
            warn!("make_result make objet new_object fail,{:?}", dominant_color_obj_r.err());
            return JObject::null().into_inner();
        }
        let dominant_color_obj = dominant_color_obj_r.unwrap();

        env.set_field(dominant_color_obj, "color", "I", JValue::Int(centroid as i32));
        env.set_field(dominant_color_obj, "percentage", "F", JValue::Float(precentage as f32));

        env.set_object_array_element(array, i as jsize, dominant_color_obj);
    }

    return array;
}