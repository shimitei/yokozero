package com.misimo.yokozero;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.text.util.Linkify;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity
		implements
		ActionBar.OnNavigationListener,
		LocationListener,
		ConnectionCallbacks,
		OnConnectionFailedListener {

	private RequestQueue mQueue;
	/**
	 * The serialization (saved instance state) Bundle key representing the
	 * current dropdown position.
	 */
	private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";
    /**
     * Note that this may be null if the Google Play services APK is not available.
     */
    private GoogleMap mMap;
    private LocationClient locationClient;
    // These settings are the same as the settings for the map. They will in fact give you updates
    // at the maximal rates currently possible.
    private static final LocationRequest REQUEST = LocationRequest.create()
            .setInterval(5000)         // 5 seconds
            .setFastestInterval(16)    // 16ms = 60fps
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Set up the action bar to show a dropdown list.
		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		// Set up the dropdown list navigation in the action bar.
		actionBar.setListNavigationCallbacks(
		// Specify a SpinnerAdapter to populate the dropdown list.
				new ArrayAdapter<String>(actionBar.getThemedContext(),
						android.R.layout.simple_list_item_1,
						android.R.id.text1, new String[] {
								getString(R.string.title_section1),
								getString(R.string.title_section2), }), this);
        setUpMapIfNeeded();
        firstPosition();
        datastrap();
	}

	static private boolean first = true;
	private void firstPosition() {
		if (first) {
			first = false;
			CameraPosition cameraPos = new CameraPosition.Builder()
			.target(new LatLng(39.2513983,140.5251469))
			.zoom(10.0f).bearing(0).build();
			moveCamera(cameraPos);
            new AlertDialog.Builder(this)
            .setTitle("注意事項")
            .setMessage("このアプリケーション内の「事故データ」は架空のデータです。\n\nその他の「学校」、「AED」の位置は実在のデータです（横手市のオープンデータを利用しています）。")
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
            	public void onClick(DialogInterface dialog, int which) {
            	}
            })
            .show();
		}
	}

	private int datamode = 0;
	private void datastrap() {
		if (datamode == 0) {
			dataSchoolYokote();
			dataAccidentYokote();
		} else if (datamode == 1) {
			dataAedYokote();
		}
//		refreshMapMarkers();
	}
	
	class MarkerData {
		public String name;
		public String snipet;
		public double lat;
		public double lng;
		public MarkerData(String name, double lat, double lng) {
			this.name = name;
			this.lat = lat;
			this.lng = lng;
		}
		public MarkerData(String name, String snipet, double lat, double lng) {
			this.name = name;
			this.snipet = snipet;
			this.lat = lat;
			this.lng = lng;
		}
	}
	private String schoolJson;
	private List<MarkerData> schoolList;
	private void dataSchoolYokote() {
		if (schoolList != null) {
			refreshMapMarkers();
			return;
		}
		if (schoolJson != null) {
			updateSchoolList(schoolJson);
			return;
		}
		progress(true);
		String url = 
				"http://linkdata.org/api/1/rdf1s845i/school_yokote_rdf.json";

		mQueue = Volley.newRequestQueue(this);
		mQueue.add(new JsonObjectRequest(Method.GET, url, null,
				new Listener<JSONObject>() {
			@Override
			public void onResponse(JSONObject response) {
				// JSONObjectのパース、List、Viewへの追加等
				schoolJson = response.toString();
				updateSchoolList(schoolJson);
				progress(false);
			}
		},

		new Response.ErrorListener() {
			@Override public void onErrorResponse(VolleyError error) {
				// エラー処理 error.networkResponseで確認
				// エラー表示など
				//TODO
				progress(false);
				Toast.makeText(getApplicationContext(), "onErrorResponse", Toast.LENGTH_LONG).show();
			}
		}));
	}
	
	private void updateSchoolList(String jsondata) {
		try {
            schoolList = new ArrayList<MainActivity.MarkerData>();
			JSONObject root = new JSONObject(jsondata);
			Iterator<String> itemIte = root.keys();
			while (itemIte.hasNext()) {
				String name = null;
				double lat = 0;
				double lng = 0;
				String itemKey = itemIte.next();
				JSONObject itemObj = (JSONObject) root.get(itemKey);
				Iterator<String> valueIte = itemObj.keys();
				while (valueIte.hasNext()) {
					String valueKey = valueIte.next();
					if (valueKey.indexOf("#name") >= 0) {
						JSONArray nameArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) nameArray.get(0);
						name = nameObj.getString("value");
					}
					if (valueKey.indexOf("#lat") >= 0) {
						JSONArray latArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) latArray.get(0);
						lat = nameObj.getDouble("value");
					}
					if (valueKey.indexOf("#long") >= 0) {
						JSONArray lngArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) lngArray.get(0);
						lng = nameObj.getDouble("value");
					}
				}
				MarkerData data = new MarkerData(name, lat, lng);
				schoolList.add(data);
			}
        } 
        catch (JSONException e) {
            // 例外処理
        	System.out.println(e);
        }
		refreshMapMarkers();
	}

//	private String accidentJson;
	private List<MarkerData> accidentList;
	private void dataAccidentYokote() {
		if (accidentList != null) {
			refreshMapMarkers();
			return;
		}
		updateAccidentList();
//		if (accidentJson != null) {
//			updateAccidentList(accidentJson);
//			return;
//		}
//		progress(true);
//		String url = 
//				"";
//
//		mQueue = Volley.newRequestQueue(this);
//		mQueue.add(new JsonObjectRequest(Method.GET, url, null,
//				new Listener<JSONObject>() {
//			@Override
//			public void onResponse(JSONObject response) {
//				// JSONObjectのパース、List、Viewへの追加等
//				accidentJson = response.toString();
//				updateAccidentList(accidentJson);
//				progress(false);
//			}
//		},
//
//		new Response.ErrorListener() {
//			@Override public void onErrorResponse(VolleyError error) {
//				// エラー処理 error.networkResponseで確認
//				// エラー表示など
//				//TODO
//				progress(false);
//				Toast.makeText(getApplicationContext(), "onErrorResponse", Toast.LENGTH_LONG).show();
//			}
//		}));
	}
	
	private void updateAccidentList() {
		accidentList = new ArrayList<MainActivity.MarkerData>();
		accidentList.add(new MarkerData("アクトライズ前",
				"事故件数: 2, 負傷者数: 2, 高齢者の負傷者数: 2, 時間別発生件数昼: 1, 時間別発生件数夕: 1, 天候別晴れ: 1, 天候別雨: 1, 前方不注意: 1, 速度超過安: 1",
				39.298433, 140.551362));
		accidentList.add(new MarkerData("丁字路",
				"事故件数: 1, 負傷者数: 1, 子供の負傷者: 1, 時間別発生件数夕: 1, 天候別曇り: 1, 一時不停止: 1, 子供の歩行中事故原因とびだし: 1",
				39.304929, 140.582458));
		accidentList.add(new MarkerData("横手南中学校前",
				"事故件数: 2, 負傷者数: 2, 子供の負傷者: 2, 時間別発生件数朝: 1, 時間別発生件数夕: 1, 天候別晴れ: 1, 天候別雨: 1, 一時不停止: 1, 子供の自転車事故原因右左折不適: 1",
				39.302799, 140.547461));
		accidentList.add(new MarkerData("下久保交差点",
				"事故件数: 1, 死亡者数: 1, 高齢者の死亡者数: 1, 時間別発生件数夕: 1, 天候別雨: 1, 前方不注意: 1",
				39.303112, 140.550334));
		accidentList.add(new MarkerData("婦気大堤婦気前",
				"事故件数: 1, 負傷者数: 1, 子供の負傷者: 1, 時間別発生件数朝: 1, 天候別雨: 1, 前方不注意: 1",
				39.301112, 140.549871));
		accidentList.add(new MarkerData("アックス前",
				"事故件数: 1, 負傷者数: 2, 高齢者の負傷者数: 2, 時間別発生件数夜: 1, 天候別雨: 1, 信号無視: 1",
				39.311947, 140.562217));
		accidentList.add(new MarkerData("ヤマダ電機裏",
				"事故件数: 2, 負傷者数: 2, 高齢者の負傷者数: 1, 時間別発生件数昼: 1, 時間別発生件数夕: 1, 天候別曇り: 1, 天候別雨: 1, 安全不確認: 1, 信号無視: 1",
				39.298917, 140.551537));
		accidentList.add(new MarkerData("JA前",
				"事故件数: 1, 負傷者数: 1, 子供の負傷者: 1, 時間別発生件数夜: 1, 天候別雨: 1, 前方不注意: 1, 子供の歩行中事故原因とびだし: 1",
				39.309163, 140.562565));
		accidentList.add(new MarkerData("横手南中学校前",
				"事故件数: 1, 負傷者数: 1, 子供の負傷者: 1, 時間別発生件数朝: 1, 天候別曇り: 1, 前方不注意: 1, 子供の歩行中事故原因直前横断: 1",
				39.302799, 140.547461));
		accidentList.add(new MarkerData("下久保交差点",
				"事故件数: 1, 負傷者数: 1, 子供の負傷者: 1, 時間別発生件数昼: 1, 天候別曇り: 1, 前方不注意: 1, 子供の自転車事故原因その他: 1",
				39.303112, 140.550334));
		refreshMapMarkers();
	}

	private String aedJson;
	private List<MarkerData> aedList;
	private void dataAedYokote() {
		if (aedList != null) {
			refreshMapMarkers();
			return;
		}
		if (aedJson != null) {
			updateAedList(aedJson);
			return;
		}
		progress(true);
		String url = 
				"http://linkdata.org/api/1/rdf1s843i/aed_yokote_rdf.json";

		mQueue = Volley.newRequestQueue(this);
		mQueue.add(new JsonObjectRequest(Method.GET, url, null,
				new Listener<JSONObject>() {
			@Override
			public void onResponse(JSONObject response) {
				// JSONObjectのパース、List、Viewへの追加等
				aedJson = response.toString();
				updateAedList(aedJson);
				progress(false);
			}
		},

		new Response.ErrorListener() {
			@Override public void onErrorResponse(VolleyError error) {
				// エラー処理 error.networkResponseで確認
				// エラー表示など
				//TODO
				progress(false);
				Toast.makeText(getApplicationContext(), "onErrorResponse", Toast.LENGTH_LONG).show();
			}
		}));
	}
	
	private void updateAedList(String jsondata) {
		try {
			aedList = new ArrayList<MainActivity.MarkerData>();
			JSONObject root = new JSONObject(jsondata);
			Iterator<String> itemIte = root.keys();
			while (itemIte.hasNext()) {
				String name = null;
				String address = "";
				String phone = "";
				String opentime = "";
				String closetime = "";
				String closureday = "";
				double lat = 0;
				double lng = 0;
				String itemKey = itemIte.next();
				JSONObject itemObj = (JSONObject) root.get(itemKey);
				Iterator<String> valueIte = itemObj.keys();
				while (valueIte.hasNext()) {
					String valueKey = valueIte.next();
					if (valueKey.indexOf("/familyName") >= 0) {
						JSONArray nameArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) nameArray.get(0);
						name = nameObj.getString("value") + ", ";
					}
					if (valueKey.indexOf("#address") >= 0) {
						JSONArray nameArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) nameArray.get(0);
						address = nameObj.getString("value") + ", ";
					}
					if (valueKey.indexOf("#phone") >= 0) {
						JSONArray nameArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) nameArray.get(0);
						phone = nameObj.getString("value") + ", ";
					}
//					if (valueKey.indexOf("#opentime") >= 0) {
//						JSONArray nameArray = itemObj.getJSONArray(valueKey);
//						JSONObject nameObj = (JSONObject) nameArray.get(0);
//						opentime = nameObj.getString("value") + ", ";
//					}
//					if (valueKey.indexOf("#closetime") >= 0) {
//						JSONArray nameArray = itemObj.getJSONArray(valueKey);
//						JSONObject nameObj = (JSONObject) nameArray.get(0);
//						closetime = nameObj.getString("value") + ", ";
//					}
//					if (valueKey.indexOf("#closureday") >= 0) {
//						JSONArray nameArray = itemObj.getJSONArray(valueKey);
//						JSONObject nameObj = (JSONObject) nameArray.get(0);
//						closureday = nameObj.getString("value") + ", ";
//					}
					if (valueKey.indexOf("#lat") >= 0) {
						JSONArray latArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) latArray.get(0);
						lat = nameObj.getDouble("value");
					}
					if (valueKey.indexOf("#long") >= 0) {
						JSONArray lngArray = itemObj.getJSONArray(valueKey);
						JSONObject nameObj = (JSONObject) lngArray.get(0);
						lng = nameObj.getDouble("value");
					}
				}
				String snipet = address + phone + opentime + closetime + closureday;
				MarkerData data = new MarkerData(name, snipet, lat, lng);
				aedList.add(data);
			}
        } 
        catch (JSONException e) {
            // 例外処理
        	System.out.println(e);
        }
		refreshMapMarkers();
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		// Restore the previously serialized current dropdown position.
		if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
			getSupportActionBar().setSelectedNavigationItem(
					savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		// Serialize the current dropdown position.
		outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, getSupportActionBar()
				.getSelectedNavigationIndex());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.menu_opendata) {
			TextView tv = new TextView(this);
            tv.setAutoLinkMask(Linkify.WEB_URLS);
            tv.setText("このアプリケーションは、秋田県横手市のオープンデータを利用しています。http://www.city.yokote.lg.jp/joho/page000006.html");
 
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
 
            new AlertDialog.Builder(this)
            .setTitle("横手市オープンデータ")
            .setView(sv)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
            	public void onClick(DialogInterface dialog, int which) {
            	}
            })
            .show();
            return true;
		} else if (id == R.id.menu_oss) {
            new AlertDialog.Builder(this)
            .setTitle("Google Play Services License")
            .setMessage(GooglePlayServicesUtil.getOpenSourceSoftwareLicenseInfo(this))
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
            	public void onClick(DialogInterface dialog, int which) {
            	}
            })
            .show();
            return true;
		} else if (id == R.id.menu_about) {
			TextView tv = new TextView(this);
            tv.setAutoLinkMask(Linkify.WEB_URLS);
            tv.setText("このアプリケーションは、ITエースをねらえ！プロジェクト第８弾 Yokoteオープンデータアイディアソン＆ハッカソンにて、チームBが開発しました。http://www.yokotecci.or.jp/itap/");
 
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
 
            new AlertDialog.Builder(this)
            .setTitle("About")
            .setView(sv)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
            	public void onClick(DialogInterface dialog, int which) {
            	}
            })
            .show();
            return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigationItemSelected(int position, long id) {
		// When the given dropdown item is selected, show its contents in the
		// container view.
		datamode = position;
		if (position == 0) {
			dataSchoolYokote();
			dataAccidentYokote();
		} else if (position == 1) {
			dataAedYokote();
		}
//		getSupportFragmentManager()
//				.beginTransaction()
//				.replace(R.id.container,
//						PlaceholderFragment.newInstance(position + 1)).commit();
		return true;
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		private static final String ARG_SECTION_NUMBER = "section_number";

		/**
		 * Returns a new instance of this fragment for the given section number.
		 */
		public static PlaceholderFragment newInstance(int sectionNumber) {
			PlaceholderFragment fragment = new PlaceholderFragment();
//			Bundle args = new Bundle();
//			args.putInt(ARG_SECTION_NUMBER, sectionNumber);
//			fragment.setArguments(args);
			return fragment;
		}

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
//			TextView textView = (TextView) rootView
//					.findViewById(R.id.section_label);
//			textView.setText(Integer.toString(getArguments().getInt(
//					ARG_SECTION_NUMBER)));
			return rootView;
		}
	}

    @Override
    protected void onResume() {
        super.onResume();
        setUpLocationClientIfNeeded();
        if (locationClient != null) {
            locationClient.connect();
        }
        setUpMapIfNeeded();
    }

    public void onPause() {
        super.onPause();
        if (locationClient != null) {
        	locationClient.disconnect();
        }
    }

    private void setUpLocationClientIfNeeded() {
        if (locationClient == null) {
            locationClient = new LocationClient(
                    getApplicationContext(),
                    this,  // ConnectionCallbacks
                    this); // OnConnectionFailedListener
        }
    }

    /**
     * Callback called when connected to GCore. Implementation of {@link ConnectionCallbacks}.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        locationClient.requestLocationUpdates(
                REQUEST,
                this);  // LocationListener
    }

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
	}

	@Override
	public void onLocationChanged(Location arg0) {
	}
    
    /**
     * Callback called when disconnected from GCore. Implementation of {@link ConnectionCallbacks}.
     */
    @Override
    public void onDisconnected() {
        // Do nothing
    }

    public Location getCurrentLocation() {
    	Location result = null;
        if (locationClient.isConnected()) {
        	// 接続されているときだけ現在地を取得
            Location lastLocation = locationClient.getLastLocation();
            result = lastLocation;
        }
        return result;
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
//        mMap.setInfoWindowAdapter(new CustomInfoWindowAdapter());
		updateMapSetting();
		refreshMapMarkers();
//		setUpMapEventListener();
    }
    
    private void refreshMapMarkers() {
    	mMap.clear();
    	if (datamode == 0) {
    		setupMarkers(schoolList, BitmapDescriptorFactory.fromResource(R.drawable.school));
    		setupMarkers(accidentList, BitmapDescriptorFactory.fromResource(R.drawable.jiko));
    	} else if (datamode == 1) {
    		setupMarkers(aedList, BitmapDescriptorFactory.fromResource(R.drawable.aed));
    	}
    }
    private void setupMarkers(List<MarkerData> list, BitmapDescriptor bd) {
    	if (list != null) {
    		int count = list.size();
    		for (int i = 0; i < count; i++) {
    			MarkerData d = list.get(i);
    			if (d != null) {
    				MarkerOptions mo = new MarkerOptions()
    				.position(new LatLng(d.lat, d.lng))
    				.title(d.name)
    				.icon(bd);
    				if (d.snipet != null && !"".equals(d.snipet)) {
    					mo.snippet(d.snipet);
    				}
    				mMap.addMarker(mo);
    			}
    		}
    	}
    }

    protected void updateMapSetting() {
    	mMap.setTrafficEnabled(false);
    	mMap.setMyLocationEnabled(true);
    	UiSettings us = mMap.getUiSettings();
    	us.setZoomControlsEnabled(true);
    	us.setCompassEnabled(true);
    	us.setScrollGesturesEnabled(true);
    	us.setZoomGesturesEnabled(true);
    	us.setTiltGesturesEnabled(true);
    	us.setRotateGesturesEnabled(true);
    }

    protected void moveCamera(CameraPosition cameraPosition) {
        if (mMap != null) {
        	mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    private void progress(boolean visible) {
    	ProgressBar progress = (ProgressBar) findViewById(R.id.progress);
    	if (progress != null) {
    		if (visible) {
    			progress.setVisibility(View.VISIBLE);
    		} else {
    			progress.setVisibility(View.INVISIBLE);
    		}
    	}
    }
}
