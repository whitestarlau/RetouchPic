package com.white.dominantColor;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 聚合出的颜色
 */
public class DominantColor implements Parcelable {

	public int color;
	public float percentage;

	public DominantColor() {
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(this.color);
		dest.writeFloat(this.percentage);
	}

	public DominantColor(int color, float percentage) {
		this.color = color;
		this.percentage = percentage;
	}

	protected DominantColor(Parcel in) {
		this.color = in.readInt();
		this.percentage = in.readFloat();
	}

	public static final Creator<DominantColor> CREATOR = new Creator<DominantColor>() {
		@Override
		public DominantColor createFromParcel(Parcel source) {
			return new DominantColor(source);
		}

		@Override
		public DominantColor[] newArray(int size) {
			return new DominantColor[size];
		}
	};
}
