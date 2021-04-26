package ru.semkin.yandexplacepicker.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.card.MaterialCardView;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.GeoObject;
import com.yandex.mapkit.GeoObjectCollection;
import com.yandex.mapkit.MapKit;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.layers.ObjectEvent;
import com.yandex.mapkit.location.FilteringMode;
import com.yandex.mapkit.location.Location;
import com.yandex.mapkit.location.LocationListener;
import com.yandex.mapkit.location.LocationManager;
import com.yandex.mapkit.location.LocationManagerUtils;
import com.yandex.mapkit.location.LocationStatus;
import com.yandex.mapkit.logo.Alignment;
import com.yandex.mapkit.logo.HorizontalAlignment;
import com.yandex.mapkit.logo.VerticalAlignment;
import com.yandex.mapkit.map.CameraListener;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.CameraUpdateReason;
import com.yandex.mapkit.map.IconStyle;
import com.yandex.mapkit.map.Map;
import com.yandex.mapkit.map.MapObject;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapObjectTapListener;
import com.yandex.mapkit.map.MapType;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.RotationType;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.search.Response;
import com.yandex.mapkit.search.SearchFactory;
import com.yandex.mapkit.search.SearchManager;
import com.yandex.mapkit.search.SearchManagerType;
import com.yandex.mapkit.search.SearchOptions;
import com.yandex.mapkit.search.SearchType;
import com.yandex.mapkit.search.Session;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.mapkit.user_location.UserLocationObjectListener;
import com.yandex.mapkit.user_location.UserLocationView;
import com.yandex.runtime.Error;
import com.yandex.runtime.image.ImageProvider;
import com.yandex.runtime.network.NetworkError;
import com.yandex.runtime.network.RemoteError;

import java.util.List;

import ru.semkin.yandexplacepicker.PlaceParcelable;
import ru.semkin.yandexplacepicker.R;
import ru.semkin.yandexplacepicker.YandexPlacePicker;

public class YandexPickerActivity extends AppCompatActivity implements UserLocationObjectListener, PlaceConfirmDialogFragment.OnPlaceConfirmedListener, MapObjectTapListener, CameraListener {

    private final static String DIALOG_CONFIRM_PLACE_TAG = "dialog_place_confirm";
    private final static int AUTOCOMPLETE_REQUEST_CODE = 1001;
    private static final double DESIRED_ACCURACY = 0;
    private static final long MINIMAL_TIME = 1000;
    private static final double MINIMAL_DISTANCE = 1;
    private static final boolean USE_IN_BACKGROUND = false;

    private MapView mMapView;
    private MapObjectCollection mMapObjects;
    private UserLocationLayer mUserLocationLayer;
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private Location mLastLocation;
    private SearchManager mSearchManager;

    private RecyclerView mRecyclerNearby;
    private PlacePickerAdapter mPlaceAdapter;

    private CoordinatorLayout mCoordinator;
    private NestedScrollView mScrollPlaces;
    private ImageButton mButtonLocation;
    private MaterialCardView mCardSearch;
    private ImageView mImageMarker;
    private TextView mTextLocationSelect;
    private TextView mTextLocation;
    private ContentLoadingProgressBar mSpinner;

    private Toolbar mToolbar;
    private AppBarLayout mAppBarLayout;

    private float mDefaultZoom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            MapKitFactory.getInstance();
        } catch (UnsatisfiedLinkError error) {
            MapKitFactory.setApiKey("4b93dcd9-0c32-442c-b888-b6ec0bc4c215");
            MapKitFactory.setLocale("ru_RU");
            MapKitFactory.initialize(this);
            SearchFactory.initialize(this);
        }

        // Initializes the map
        setContentView(R.layout.activity_place_picker);
        mMapView = findViewById(R.id.mapview);
        mMapView.getMap().setMapType(MapType.MAP);
        mMapView.getMap().setRotateGesturesEnabled(false);
        mMapView.getMap().getLogo().setAlignment(new Alignment(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM));

        mCoordinator = findViewById(R.id.coordinator);
        mScrollPlaces = findViewById(R.id.svPlaces);
        mRecyclerNearby = findViewById(R.id.rvNearbyPlaces);
        mButtonLocation = findViewById(R.id.btnMyLocation);
        mCardSearch = findViewById(R.id.cardSearch);
        mImageMarker = findViewById(R.id.ivMarkerSelect);
        mTextLocationSelect = findViewById(R.id.tvLocationSelect);
        mTextLocation = findViewById(R.id.tvLocationSelectAddr);
        mSpinner = findViewById(R.id.pbLoading);
        mToolbar = findViewById(R.id.toolbar);
        mAppBarLayout = findViewById(R.id.appBarLayout);

        // Configure the toolbar
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Construct a UserLocationLayer
        mUserLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mMapView.getMapWindow());
        mUserLocationLayer.setObjectListener(this);
        mUserLocationLayer.setVisible(true);

        // Construct a MapObjects
        mMapObjects = mMapView.getMap().getMapObjects().addCollection();

        // Construct LocationManager and SearchManager
        mLocationManager = MapKitFactory.getInstance().createLocationManager();
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationUpdated(@NonNull Location location) {
                mLastLocation = location;
                animateCamera(location.getPosition(), mDefaultZoom);

                // Load the places near this location
                loadNearbyPlaces();
            }

            @Override
            public void onLocationStatusUpdated(@NonNull LocationStatus locationStatus) {
                if (locationStatus == LocationStatus.NOT_AVAILABLE) {
                    //todo handle no geo
                }
            }
        };

        mSearchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.ONLINE);
        // Sets the default zoom and location
        mMapView.getMap().move(new CameraPosition(new Point(55.6842731688303, 37.6219312108), 4.716611f, 0, 0));
        mMapView.getMap().addCameraListener(this);

        // Sets the default zoom
        mDefaultZoom = getResources().getInteger(R.integer.default_zoom);

        // Initialize the UI
        initializeUi();

        // Restore any active fragment
        restoreFragments();

        // Use the last know location to point the map to
        mLastLocation = LocationManagerUtils.getLastKnownLocation();
        if(mLastLocation != null) {
            animateCamera(mLastLocation.getPosition(), mDefaultZoom);
            loadNearbyPlaces();
        }
    }

    @Override
    public void onObjectAdded(@NonNull UserLocationView userLocationView) {
        //userLocationView.getAccuracyCircle().setFillColor(Color.BLUE);

        userLocationView.getArrow().setIcon(
                ImageProvider.fromResource(this, R.drawable.ic_location_green),
                new IconStyle().setAnchor(new PointF(0.5f, 0.5f))
                        .setRotationType(RotationType.ROTATE)
                        .setZIndex(1f)
                        .setScale(0.5f)
        );

        userLocationView.getPin().setIcon(
                ImageProvider.fromResource(this, R.drawable.ic_location_green),
                new IconStyle().setAnchor(new PointF(0.5f, 0.5f))
                        .setRotationType(RotationType.ROTATE)
                        .setZIndex(1f)
                        .setScale(0.5f)
        );
    }

    @Override
    public void onObjectRemoved(@NonNull UserLocationView userLocationView) {

    }

    @Override
    public void onObjectUpdated(@NonNull UserLocationView userLocationView, @NonNull ObjectEvent objectEvent) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_place_picker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (android.R.id.home == item.getItemId()) {
            finish();
            return true;
        }

        if (R.id.action_search == item.getItemId()) {
            requestPlacesSearch();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeUi() {
        // Initialize the recycler view
        mRecyclerNearby.setLayoutManager(new LinearLayoutManager(this));

        // Bind the listeners
        mButtonLocation.setOnClickListener(v -> onFabCurrentLocationClick());
        mCardSearch.setOnClickListener(v -> requestPlacesSearch());
        mImageMarker.setOnClickListener(v -> selectThisPlace());
        mTextLocationSelect.setOnClickListener(v -> selectThisPlace());

        // Hide or show the card search according to the width
        int visibility = View.GONE;
        if (getResources().getBoolean(R.bool.show_card_search)) visibility = View.VISIBLE;
        mCardSearch.setVisibility(visibility);

        // Add a nice fade effect to toolbar
        mAppBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) ->
                mToolbar.setAlpha(Math.abs(verticalOffset / (float) appBarLayout.getTotalScrollRange()))
        );
        // Set default behavior
        CoordinatorLayout.LayoutParams appBarLayoutParams = (CoordinatorLayout.LayoutParams) mAppBarLayout.getLayoutParams();
        appBarLayoutParams.setBehavior(new AppBarLayout.Behavior());
        // Disable the drag
        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) appBarLayoutParams.getBehavior();
        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
                return false;
            }
        });

        // Set the size of AppBarLayout to 68% of the total height
        if (ViewCompat.isLaidOut(mCoordinator) && !mCoordinator.isLayoutRequested()) {
            measure(appBarLayoutParams, mCoordinator);
        } else { // .doOnLayout doesn't exist in Java
            mCoordinator.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.removeOnLayoutChangeListener(this);
                    measure(appBarLayoutParams, v);
                }
            });
        }
    }

    private void measure(CoordinatorLayout.LayoutParams appBarLayoutParams, View view) {
        if (getResources().getBoolean(R.bool.show_confirmation_buttons))
            appBarLayoutParams.height = (view.getHeight() * 68) / 100;
        else
            appBarLayoutParams.height = (view.getHeight() * 85) / 100;
    }

    private void subscribeToLocationUpdate() {
        if (mLocationManager != null && mLocationListener != null) {
            mLocationManager.subscribeForLocationUpdates(DESIRED_ACCURACY, MINIMAL_TIME, MINIMAL_DISTANCE, USE_IN_BACKGROUND, FilteringMode.OFF, mLocationListener);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
        MapKitFactory.getInstance().onStart();

        subscribeToLocationUpdate();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
        MapKitFactory.getInstance().onStop();
        mLocationManager.unsubscribe(mLocationListener);
    }

    public void onFabCurrentLocationClick() {
        if (mLastLocation == null) {
            return;
        }

        animateCamera(mLastLocation.getPosition(), mDefaultZoom);
    }

    @Override
    public void onResume() {
        super.onResume();
        mLocationManager.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mLocationManager.suspend();
    }

    private void animateCamera(Point point, float zoom) {
        mMapView.getMap().move(
                new CameraPosition(point, zoom, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 1),
                null);
    }

    private void moveCamera(Point point, float zoom) {
        mMapView.getMap().move(
                new CameraPosition(point, zoom, 0.0f, 0.0f));
    }

    private void showAddrButton(GeoObject place) {
        mTextLocation.setVisibility(View.VISIBLE);
        mTextLocation.setText(place.getName());

        mTextLocationSelect.setText(R.string.picker_confirm_this_location);

        mImageMarker.setOnClickListener(v -> showConfirmPlacePopup(place));
        mTextLocationSelect.setOnClickListener(v -> showConfirmPlacePopup(place));
        mTextLocation.setOnClickListener(v -> showConfirmPlacePopup(place));
    }

    private void hideAddrButton() {
        mTextLocation.setVisibility(View.GONE);

        mTextLocationSelect.setText(R.string.picker_search_this);

        mImageMarker.setOnClickListener(v -> selectThisPlace());
        mTextLocationSelect.setOnClickListener(v -> selectThisPlace());
    }

    private final Session.SearchListener mSearchNearbyListener = new Session.SearchListener() {
        @Override
        public void onSearchResponse(@NonNull Response response) {
            GeoObject toponym = response.getMetadata().getToponym();
            if(toponym != null) {
                showAddrButton(toponym);
            }
            if(response.getCollection().getChildren().size() > 0) {
                bindPlaces(response.getCollection().getChildren());
            }
            mSpinner.hide();
        }

        @Override
        public void onSearchError(@NonNull Error error) {
            String errorMessage = getString(R.string.unknown_error_message);
            if (error instanceof RemoteError) {
                errorMessage = getString(R.string.remote_error_message);
            } else if (error instanceof NetworkError) {
                errorMessage = getString(R.string.network_error_message);
            }
            Log.e(YandexPickerActivity.class.getCanonicalName(), errorMessage);

            Toast.makeText(YandexPickerActivity.this, R.string.picker_load_places_error, Toast.LENGTH_SHORT).show();
            mSpinner.hide();
        }
    };

    private void loadNearbyPlaces() {
        SearchOptions options = new SearchOptions();
        options.setSearchTypes(SearchType.BIZ.value);
        mSpinner.show();
        mSearchManager.submit(mLastLocation.getPosition(), 20, options, mSearchNearbyListener);
    } //todo session ignored

    private void selectThisPlace() {
        SearchOptions options = new SearchOptions();
        options.setSearchTypes(SearchType.BIZ.value);
        mSpinner.show();
        Point center = mMapView.getMap().getCameraPosition().getTarget();
        mSearchManager.submit(center, 20, options, mSearchNearbyListener);
    } //todo session ignored

    private void bindPlaces(List<GeoObjectCollection.Item> places) {
        // Bind to the recycler view
        if(mPlaceAdapter == null) {
            mPlaceAdapter = new PlacePickerAdapter(places, false, onPlaceListener);
        } else {
            mPlaceAdapter.swapData(places);
        }
        mRecyclerNearby.setAdapter(mPlaceAdapter);

        if (getResources().getBoolean(R.bool.show_pins_on_map)) {
            // Bind to the map
            mMapObjects.clear();
            for (GeoObjectCollection.Item collectionItem : places) {
                GeoObject place = collectionItem.getObj();
                if(place != null) {
                    addMarker(place);
                }
            }
        }
    }

    private final PlacePickerAdapter.OnPlaceListener onPlaceListener = new PlacePickerAdapter.OnPlaceListener() {
        @Override
        public void onPlaceSelected(GeoObject place) {
            showConfirmPlacePopup(place);
        }

        @Override
        public void onPlacePreviewed(GeoObject place) {
            animateCamera(place.getGeometry().get(0).getPoint(), mDefaultZoom);
            showAddrButton(place);
            collapseToolbar();
        }
    };

    private void collapseToolbar() {
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) mAppBarLayout.getLayoutParams();

        mScrollPlaces.scrollTo(0, 0);

        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
        if(behavior != null) {
            behavior.setTopAndBottomOffset(0);
        }
    }

    private PlacemarkMapObject addMarker(GeoObject place) {
        Point point = place.getGeometry().get(0).getPoint();
        if(point != null) {
            PlacemarkMapObject placemark = mMapObjects.addPlacemark(point, getPlaceMarkerBitmap(place));
            placemark.setUserData(place);
            placemark.addTapListener(this);
            return placemark;
        }
        return null;
    }

    @Override
    public boolean onMapObjectTap(@NonNull MapObject mapObject, @NonNull Point point) {
        showConfirmPlacePopup((GeoObject) mapObject.getUserData());
        return true;
    }

    private void showConfirmPlacePopup(GeoObject place) {
        if (getResources().getBoolean(R.bool.show_confirmation_dialog)) {
            PlaceConfirmDialogFragment fragment = PlaceConfirmDialogFragment.newInstance(place, this);
            fragment.show(getSupportFragmentManager(), DIALOG_CONFIRM_PLACE_TAG);
        } else {
            onPlaceConfirmed(new PlaceParcelable(place));
        }
    }

    @Override
    public void onPlaceConfirmed(PlaceParcelable place) {
        Intent data = new Intent();
        data.putExtra(YandexPlacePicker.EXTRA_PLACE, place);
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    private ImageProvider getPlaceMarkerBitmap(GeoObject place) {
        int innerIconSize = getResources().getDimensionPixelSize(R.dimen.marker_inner_icon_size);

        Drawable bgDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_map_marker_solid_red_32dp, null);

        Drawable fgDrawable = ResourcesCompat.getDrawable(getResources(), UiUtils.getPlaceDrawableRes(this, place), null);
        DrawableCompat.setTint(fgDrawable, getResources().getColor(R.color.colorMarkerInnerIcon));

        Bitmap bitmap = Bitmap.createBitmap(bgDrawable.getIntrinsicWidth(), bgDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);

        bgDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());

        int left = (canvas.getWidth() - innerIconSize) / 2;
        int top = (canvas.getHeight() - innerIconSize) / 3;
        int right = left + innerIconSize;
        int bottom = top + innerIconSize;

        fgDrawable.setBounds(left, top, right, bottom);

        bgDrawable.draw(canvas);
        fgDrawable.draw(canvas);

        return ImageProvider.fromBitmap(bitmap);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == AUTOCOMPLETE_REQUEST_CODE) && (resultCode == Activity.RESULT_OK)) {
            PlaceParcelable place = SearchActivity.getPlace(data);
            onPlaceConfirmed(place);
        }
    }

    private void requestPlacesSearch() {
        Intent intent = new Intent(this, SearchActivity.class);
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);
    }

    private void restoreFragments() {
        PlaceConfirmDialogFragment fragment =
                (PlaceConfirmDialogFragment) getSupportFragmentManager().findFragmentByTag(DIALOG_CONFIRM_PLACE_TAG);
        if(fragment != null) fragment.confirmListener = this;
    }

    @Override
    public void onCameraPositionChanged(@NonNull Map map, @NonNull CameraPosition cameraPosition, @NonNull CameraUpdateReason cameraUpdateReason, boolean finished) {
        if (cameraUpdateReason == CameraUpdateReason.GESTURES) {
            if (getResources().getBoolean(R.bool.search_on_scroll)) {
                if(finished)
                    selectThisPlace();
            }
            else {
                hideAddrButton();
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MapKitFactory.getInstance().onStop();
        mLocationManager.unsubscribe(mLocationListener);
        mMapView.onStop();
    }
}
