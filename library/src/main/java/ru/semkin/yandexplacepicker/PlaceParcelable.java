package ru.semkin.yandexplacepicker;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.yandex.mapkit.GeoObject;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.search.Address;
import com.yandex.mapkit.search.ToponymObjectMetadata;

import java.util.List;

public class PlaceParcelable implements Parcelable {

    private String mName;
    private String mAddress;
    private String mHousePresent;
    private PointParcelable mPoint;

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mAddress);
        dest.writeString(mHousePresent);
        dest.writeParcelable(mPoint, 0);
    }

    private PlaceParcelable(Parcel in) {
        mName = in.readString();
        mAddress = in.readString();
        mHousePresent = in.readString();
        mPoint = in.readParcelable(PointParcelable.class.getClassLoader());
    }

    public PlaceParcelable(GeoObject place) {
        mName = place.getName();
        mAddress = place.getDescriptionText();
        mHousePresent = "";

        ToponymObjectMetadata metadata = place.getMetadataContainer().getItem(ToponymObjectMetadata.class);
        for (Address.Component component : metadata.getAddress().getComponents()) {
            List<Address.Component.Kind> kinds = component.getKinds();
            for (Address.Component.Kind kind : kinds) {
                if (kind.equals(Address.Component.Kind.HOUSE)) {
                    mHousePresent = component.getName();
                }
            }
        }

        Point point = place.getGeometry().get(0).getPoint();
        if(point != null) {
            mPoint = new PointParcelable(point);
        }
    }

    public PlaceParcelable() { }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PlaceParcelable> CREATOR = new Creator<PlaceParcelable>() {
        @Override
        public PlaceParcelable createFromParcel(Parcel in) {
            return new PlaceParcelable(in);
        }

        @Override
        public PlaceParcelable[] newArray(int size) {
            return new PlaceParcelable[size];
        }
    };

    public String getName() {
        return mName;
    }

    public String getAddress() {
        return mAddress;
    }

    public String getHousePresent() {
        return mHousePresent;
    }

    public PointParcelable getPoint() {
        return mPoint;
    }
}
