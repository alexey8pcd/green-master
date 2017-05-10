package ru.alexey_ovcharov.greenguide.mobile.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import ru.alexey_ovcharov.greenguide.mobile.Commons;
import ru.alexey_ovcharov.greenguide.mobile.persist.DbHelper;
import ru.alexey_ovcharov.greenguide.mobile.persist.PersistenceException;
import ru.alexey_ovcharov.greenguide.mobile.persist.Place;
import ru.alexey_ovcharov.greenguide.mobile.persist.PlaceType;

import static ru.alexey_ovcharov.greenguide.mobile.Commons.APP_NAME;

public class PublicationService extends Service {

    private static final int NOTIFY_ID = 101;
    private DbHelper dbHelper;

    public PublicationService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DbHelper(getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsync();
        return super.onStartCommand(intent, flags, startId);
    }

    private void startAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(APP_NAME, "Начинаю отправку данных на сервер");
                    String data = prepareTextRequest();
                    NetworkStatus networkStatus = NetworkStatus.CLIENT_ERROR;
                    if (data != null) {
                        networkStatus = sendData(data);
                    }
                    sendNotify(networkStatus);
                } catch (Exception ex) {
                    Log.e(APP_NAME, ex.toString(), ex);
                } finally {
                    stopSelf();
                }

            }
        }).start();
    }

    private void sendNotify(NetworkStatus networkStatus) {
        Log.d(APP_NAME, "Создаю уведомление об отправке");
        Context context = getApplicationContext();
        Resources res = context.getResources();
        Notification.Builder builder = new Notification.Builder(context);

        String textRes = "?";
        int icon = android.R.drawable.ic_dialog_alert;
        switch (networkStatus) {
            case SUCCESS:
                textRes = "Успешная отправка";
                icon = android.R.drawable.ic_dialog_info;
                break;
            case CLIENT_ERROR:
                textRes = "Ошибка: неверный запрос";
                break;
            case SERVER_ERROR:
                textRes = "Ошибка сервера";
                break;
            case UNKNOWN:
                textRes = "Ошибка сети";
                break;
        }
        builder.setSmallIcon(icon)
//        builder.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Результат публикации справочников")
                .setContentText(textRes);
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFY_ID, notification);

    }

    @Nullable
    private String prepareTextRequest() {
        try {
            JSONObject requestJSON = new JSONObject();
            List<PlaceType> placesTypes = dbHelper.getPlacesTypes();
            JSONArray placeTypesJsonArray = createPlaceTypes(placesTypes);
            JSONArray placesJsonArray = createPlaces(placesTypes);
            requestJSON.put(Place.TABLE_NAME, placesJsonArray);
            requestJSON.put(PlaceType.TABLE_NAME, placeTypesJsonArray);
            requestJSON.put(DbHelper.DATABASE_ID, dbHelper.getSettingByName(DbHelper.DATABASE_ID));
            return requestJSON.toString();
        } catch (Exception e) {
            Log.e(APP_NAME, e.toString(), e);
        }
        return null;
    }

    @NonNull
    private JSONArray createPlaces(List<PlaceType> placesTypes) throws PersistenceException,
            FileNotFoundException, JSONException {

        Map<Integer, String> countries = dbHelper.getCountries();

        JSONArray jsonArray = new JSONArray();
        for (PlaceType placeType : placesTypes) {
            List<Place> places = dbHelper.getPlacesByTypeWithImages(placeType.getIdPlaceType());
            for (Place place : places) {
                JSONObject jsonObject = place.toJsonObject(countries, getApplicationContext());
                jsonArray.put(jsonObject);
            }
        }
        return jsonArray;
    }

    @NonNull
    private JSONArray createPlaceTypes(List<PlaceType> placesTypes) throws JSONException {

        JSONArray jsonArray = new JSONArray();
        for (PlaceType placeType : placesTypes) {
            JSONObject placeTypeObject = placeType.toJSONObject();
            jsonArray.put(placeTypeObject);
        }
        return jsonArray;
    }

    private NetworkStatus sendData(String data) {
        try {
            String serviceUrl = dbHelper.getSettingByName(Commons.SERVER_URL);
            URL url = new URL(serviceUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(250000);
            conn.setConnectTimeout(25000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", "Android");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setDoOutput(true);
            Log.d(APP_NAME, "Отправляю запрос на сервер: " + data);
            OutputStream outputStream = conn.getOutputStream();
            outputStream.write(data.getBytes("utf-8"));
            outputStream.flush();
            int responseCode = conn.getResponseCode();
            Log.d(APP_NAME, "Http код ответа: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return NetworkStatus.SUCCESS;
            } else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                return NetworkStatus.CLIENT_ERROR;
            } else {
                return NetworkStatus.SERVER_ERROR;
            }
        } catch (Exception e) {
            Log.e(APP_NAME, e.toString(), e);
            return NetworkStatus.UNKNOWN;
        }

    }
}
