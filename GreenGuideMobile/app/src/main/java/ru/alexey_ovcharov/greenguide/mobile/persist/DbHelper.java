package ru.alexey_ovcharov.greenguide.mobile.persist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ru.alexey_ovcharov.greenguide.mobile.Commons.APP_NAME;

/**
 * Created by Алексей on 23.04.2017.
 */

public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "green_guide_db";
    private static final int VERSION = 1;
    public static final int ROW_NOT_INSERTED = -1;
    public static final String SETTINGS_TABLE_NAME = "settings";
    public static final String SETTINGS_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS "
            + SETTINGS_TABLE_NAME + "(name VARCHAR PRIMARY KEY NOT NULL UNIQUE, value TEXT)";
    public static final String DATABASE_ID = "database_id";
    public static final String NAME_COLUMN = "name";
    public static final String VALUE_COLUMN = "value";

    public DbHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(APP_NAME, "onCreate");
        db.execSQL(SETTINGS_TABLE_CREATE);
        db.execSQL("delete from " + SETTINGS_TABLE_NAME);
        ContentValues values = new ContentValues();
        values.put(NAME_COLUMN, DATABASE_ID);
        values.put(VALUE_COLUMN, UUID.randomUUID().toString());
        db.insert(SETTINGS_TABLE_NAME, null, values);
        db.execSQL(Image.CREATE_SCRIPT);
        db.execSQL(Country.CREATE_SCRIPT);
        db.execSQL(CategoryOfThing.CREATE_SCRIPT);
        db.execSQL(PlaceType.CREATE_SCRIPT);
        db.execSQL(Place.CREATE_SCRIPT);
        db.execSQL(Place.IMAGE_FOR_PLACES_CREATE_SCRIPT);
        db.execSQL(NoteType.CREATE_SCRIPT);
        db.execSQL(Note.CREATE_SCRIPT);
        db.execSQL(Thing.CREATE_SCRIPT);
        db.execSQL(Thing.IMAGE_FOR_THING_CREATE_SCRIPT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(APP_NAME, "onUpgrade");
        db.execSQL(Thing.IMAGE_FOR_THING_DROP_SCRIPT);
        db.execSQL(Thing.DROP_SCRIPT);
        db.execSQL(Note.DROP_SCRIPT);
        db.execSQL(NoteType.DROP_SCRIPT);
        db.execSQL(Place.IMAGE_FOR_PLACES_DROP_SCRIPT);
        db.execSQL(Place.DROP_SCRIPT);
        db.execSQL(PlaceType.DROP_SCRIPT);
        db.execSQL(CategoryOfThing.DROP_SCRIPT);
        db.execSQL(Country.DROP_SCRIPT);
        db.execSQL(Image.DROP_SCRIPT);
        onCreate(db);
    }

    @Nullable
    public String getSettingByName(String name) {
        try {
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursor = database.rawQuery("select value from " + SETTINGS_TABLE_NAME
                    + " where name='" + name.replaceAll("'", "\"") + "'", null)) {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndex("value"));
                }
            }
        } catch (Exception ex) {
            Log.e(APP_NAME, ex.toString(), ex);
        }
        return null;
    }

    public void putSetting(String name, String value) {
        try {
            name = name.replaceAll("'", "\"");
            value = value.replaceAll("'", "\"");
            SQLiteDatabase database = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("value", value);
            if (getSettingByName(name) != null) {
                int updatedRowsCount = database.update(SETTINGS_TABLE_NAME, cv, "name = ?", new String[]{name});
            } else {
                cv.put("name", name);
                long insertRowId = database.insert(SETTINGS_TABLE_NAME, null, cv);
            }
        } catch (Exception ex) {
            Log.e(APP_NAME, ex.toString(), ex);
        }
    }

    @NonNull
    public List<PlaceType> getPlacesTypes() {
        Log.d(APP_NAME, "Выбираю типы мест из базы");
        List<PlaceType> placeTypes = Collections.EMPTY_LIST;
        try {
            SQLiteDatabase sqLiteDatabase = getReadableDatabase();
            Cursor cursor = sqLiteDatabase.rawQuery(
                    "select * from place_types", null);
            if (cursor.moveToFirst()) {
                placeTypes = new ArrayList<>();
                do {
                    int idPlaceType = cursor.getInt(cursor.getColumnIndex(PlaceType.ID_PLACE_TYPE_COLUMN));
                    String type = cursor.getString(cursor.getColumnIndex(PlaceType.TYPE_COLUMN));
                    placeTypes.add(new PlaceType(idPlaceType, type));
                } while (cursor.moveToNext());

            }
        } catch (Exception e) {
            Log.e(APP_NAME, e.toString(), e);
        }
        Log.d(APP_NAME, "Выбрал типов: " + placeTypes.size());
        return placeTypes;
    }

    public void addPlaceType(String placeType) throws PersistenceException {
        Log.d(APP_NAME, "Добавляю новый тип мест: " + placeType);
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(PlaceType.TYPE_COLUMN, placeType);
            long l = database.insert(PlaceType.TABLE_NAME, null, values);
            Log.d(APP_NAME, "Ид созданной строки: " + l);
        } catch (Exception e) {
            throw new PersistenceException("Не удалось добавить тип мест ", e);
        }
    }

    @NonNull
    public List<Place> getPlacesByType(int placeTypeId, boolean withImages) throws PersistenceException {
        Log.d(APP_NAME, "Выбираю места из базы");
        List<Place> places = Collections.EMPTY_LIST;
        try {
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursor = database.rawQuery("select * " +
                    "from places where id_place_type = " + placeTypeId, null)) {
                if (cursor.moveToFirst()) {
                    places = new ArrayList<>();
                    do {
                        Place place = selectPlace(cursor);
                        places.add(place);
                        if (withImages) {
                            List<Integer> imagesId = getImagesForPlace(place.getIdPlace(), database);
                            place.addImageIds(imagesId);
                        }
                    } while (cursor.moveToNext());
                }
            }
            Log.d(APP_NAME, "Выбрано мест: " + places.size());
        } catch (Exception e) {
            throw new PersistenceException("Ошибка при получении мест", e);
        }
        return places;
    }

    public int getPlacesCount() throws PersistenceException {
        Log.d(APP_NAME, "Выполняю получение количество мест в базе");
        try {
            int placesCount;
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursor = database.rawQuery("select count(*) as places_count from " + Place.TABLE_NAME, null)) {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(cursor.getColumnIndex("places_count"));
                } else {
                    throw new SQLException("Ошибка при получении количества мест в базе");
                }
            }
        } catch (Exception e) {
            throw new PersistenceException("Не удалось получить количество мест ", e);
        }
    }

    public Map<Integer, String> getCountries() throws PersistenceException {
        Log.d(APP_NAME, "Получаю список стран");
        try {
            Map<Integer, String> countries = new HashMap<>();
            SQLiteDatabase database = getReadableDatabase();
            Cursor cursor = database.rawQuery("select * from " + Country.TABLE_NAME, null);
            if (cursor.moveToFirst()) {
                do {
                    String countryName = cursor.getString(cursor.getColumnIndex(Country.COUNTRY_COLUMN));
                    int idCountry = cursor.getInt(cursor.getColumnIndex(Country.ID_COUNTRY_COLUMN));
                    countries.put(idCountry, countryName);
                } while (cursor.moveToNext());
            }
            Log.d(APP_NAME, "Найдено стран: " + countries.size());
            return countries;
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось получить список стран", ex);
        }
    }

    public long bindImageForPlace(int idImage, int idPlace) throws PersistenceException {
        Log.d(APP_NAME, "Добавляю изображение с ид " + idImage + " для места с ид " + idPlace);
        try {
            ContentValues cv = new ContentValues();
            cv.put(Image.ID_IMAGE_COLUMN, idImage);
            cv.put(Place.ID_PLACE_COLUMN, idPlace);
            long rowNum = getWritableDatabase().insert(Place.IMAGES_FOR_PLACE_TABLE_NAME, null, cv);
            if (rowNum != -1) {
                Log.d(APP_NAME, "Изображение добавлено для места");
            }
            return rowNum;
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось добавить", ex);
        }
    }

    public long addImage(byte[] imageBytes, String imageUrl) throws PersistenceException {
        Log.d(APP_NAME, "Вставляю новое изображение");
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            if (imageBytes != null) {
                values.put(Image.BINARY_DATA_COLUMN, imageBytes);
            }
            if (imageUrl != null) {
                values.put(Image.URL_COLUMN, imageUrl);
            }
            long rowNum = database.insert(Image.TABLE_NAME, null, values);
            if (rowNum != -1) {
                Log.d(APP_NAME, "Изображение вставлено");
            } else {
                Log.d(APP_NAME, "Не удалось вставить изображение");
            }
            return rowNum;
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось вставить изображение", ex);
        }
    }

    @NonNull
    public List<Image> getImageData(List<String> idsImage) throws PersistenceException {
        Log.d(APP_NAME, "Получаю данные изображения по ид: " + idsImage);
        try {
            SQLiteDatabase database = getReadableDatabase();
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (String idImage : idsImage) {
                if (!first) {
                    builder.append(",");
                } else {
                    first = false;
                }
                builder.append(idImage);
            }
            Cursor cursor = database.rawQuery("select * from "
                    + Image.TABLE_NAME + " where "
                    + Image.ID_IMAGE_COLUMN + " in (" + builder.toString() + ")", null);

            List<Image> imageList = new ArrayList<>();
            if (cursor.moveToFirst()) {
                do {
                    int idImage = cursor.getInt(cursor.getColumnIndex(Image.ID_IMAGE_COLUMN));
                    byte[] imageData = cursor.getBlob(cursor.getColumnIndex(Image.BINARY_DATA_COLUMN));
                    String imageUrl = cursor.getString(cursor.getColumnIndex(Image.URL_COLUMN));
                    Image image = new Image();
                    image.setIdImage(idImage);
                    image.setUrl(imageUrl);
                    image.setBinaryData(imageData);
                    imageList.add(image);
                } while (cursor.moveToNext());
            }
            Log.d(APP_NAME, "Выбрано изображений: " + imageList.size());
            return imageList;
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось получить изображения", ex);
        }
    }

    @NonNull
    private Place selectPlace(Cursor cursor) throws ParseException {
        Place place = new Place();
        int placeTypeId = cursor.getInt(cursor.getColumnIndex(Place.ID_PLACE_TYPE_COLUMN));
        place.setIdPlaceType(placeTypeId);
        int placeId = cursor.getInt(cursor.getColumnIndex(Place.ID_PLACE_COLUMN));
        place.setIdPlace(placeId);

        int columnIndexAddress = cursor.getColumnIndex(Place.ADDRESS_COLUMN);
        String address = cursor.getString(columnIndexAddress);
        if (!cursor.isNull(columnIndexAddress) && !"null".equals(address)) {
            place.setAddress(address);
        }
        String description = cursor.getString(cursor.getColumnIndex(Place.DESCRIPTION_COLUMN));
        place.setDescription(description);
        String dateCreate = cursor.getString(cursor.getColumnIndex(Place.DATE_CREATE_COLUMN));
        place.setDateCreate(dateCreate);
        int columnIndexCountry = cursor.getColumnIndex(Place.ID_COUNTRY_COLUMN);
        Integer idCountry = cursor.getInt(columnIndexCountry);
        if (cursor.isNull(columnIndexCountry)) {
            idCountry = null;
        }
        place.setIdCountry(idCountry);

        BigDecimal latitude;
        int columnIndexLatitude = cursor.getColumnIndex(Place.LATITUDE_COLUMN);
        if (cursor.isNull(columnIndexLatitude)) {
            latitude = null;
        } else {
            latitude = new BigDecimal(cursor.getString(columnIndexLatitude));
        }
        place.setLatitude(latitude);

        BigDecimal longitude;
        int columnIndexLongitude = cursor.getColumnIndex(Place.LONGITUDE_COLUMN);
        if (cursor.isNull(columnIndexLongitude)) {
            longitude = null;
        } else {
            longitude = new BigDecimal(cursor.getString(columnIndexLongitude));
        }
        place.setLongitude(longitude);
        return place;
    }

    public void addPlace(Place place) throws PersistenceException {
        Log.d(APP_NAME, "Добавляю новое место: " + place);
        SQLiteDatabase database = null;
        try {
            database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Place.ADDRESS_COLUMN, place.getAddress());
            values.put(Place.DATE_CREATE_COLUMN, place.getDateCreate());
            values.put(Place.DESCRIPTION_COLUMN, place.getDescription());
            values.put(Place.ID_COUNTRY_COLUMN, place.getIdCountry());
            values.put(Place.ID_PLACE_TYPE_COLUMN, place.getIdPlaceType());
            if (place.getLatitude() != null) {
                values.put(Place.LATITUDE_COLUMN, place.getLatitude().toString());
            }
            if (place.getLongitude() != null) {
                values.put(Place.LONGITUDE_COLUMN, place.getLongitude().toString());
            }
            List<Integer> imagesIds = place.getImagesIds();
            database.beginTransaction();
            long idPlace = database.insert(Place.TABLE_NAME, null, values);
            if (idPlace != ROW_NOT_INSERTED) {
                for (Integer idImage : imagesIds) {
                    ContentValues imageCv = new ContentValues(2);
                    imageCv.put(Image.ID_IMAGE_COLUMN, idImage);
                    imageCv.put(Place.ID_PLACE_COLUMN, idPlace);
                    long insert = database.insert(Place.IMAGES_FOR_PLACE_TABLE_NAME, null, imageCv);
                    if (insert == ROW_NOT_INSERTED) {
                        throw new Exception("Не удалось добавить изображение для места с ид: "
                                + idPlace + ", выполняю откат");
                    }
                }
                database.setTransactionSuccessful();
                Log.d(APP_NAME, "Место добавлено, ид: " + idPlace);
            }
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось добавить место", ex);
        } finally {
            if (database != null) {
                database.endTransaction();
            }
        }
    }


    public void addCategoryOfThing(String categoryName) throws PersistenceException {
        Log.d(APP_NAME, "Создаю новую категорию вещей: " + categoryName);
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(CategoryOfThing.CATEGORY_COLUMN, categoryName);
            long l = database.insert(CategoryOfThing.TABLE_NAME,
                    CategoryOfThing.ID_CATEGORY_COLUMN, values);
            Log.d(APP_NAME, "Ид созданной строки: " + l);
        } catch (Exception e) {
            throw new PersistenceException("Не удалось добавить категорию вещей", e);
        }
    }

    public Place getPlaceWithImages(int placeId) throws PersistenceException {
        Log.d(APP_NAME, "Выбираю место с изображениями, ид: " + placeId);
        try {
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursorPlace = database.rawQuery("select *" +
                    " from places where id_place = " + placeId, null)) {
                if (cursorPlace.moveToFirst()) {
                    Place place = selectPlace(cursorPlace);
                    List<Integer> imagesId = getImagesForPlace(placeId, database);
                    place.addImageIds(imagesId);
                    return place;
                } else {
                    throw new PersistenceException("Не найдено место с ид: " + placeId);
                }
            }
        } catch (Exception e) {
            throw new PersistenceException("Не удалось получить информацию для места с ид: " + placeId, e);
        }
    }

    @NonNull
    private List<Integer> getImagesForPlace(int placeId, SQLiteDatabase database) {
        List<Integer> imagesId = new ArrayList<>();
        try (Cursor cursorImages = database.rawQuery("select " + Image.ID_IMAGE_COLUMN
                + " from images_for_place " +
                " where id_place = " + placeId, null)) {
            if (cursorImages.moveToFirst()) {
                do {
                    int idImage = cursorImages.getInt(cursorImages.getColumnIndex(Image.ID_IMAGE_COLUMN));
                    if (idImage != ROW_NOT_INSERTED) {
                        imagesId.add(idImage);
                    }
                } while (cursorImages.moveToNext());
            }
        }
        Log.d(APP_NAME, "Выбрано изображений: " + imagesId.size());
        return imagesId;
    }

    public void updatePlace(Place place) throws PersistenceException {
        int idPlace = place.getIdPlace();
        Log.d(APP_NAME, "Обновляю место по ид: " + idPlace);
        SQLiteDatabase database = getWritableDatabase();
        try {
            ContentValues upPlaceData = new ContentValues(6);
            upPlaceData.put(Place.ADDRESS_COLUMN, place.getAddress());
            upPlaceData.put(Place.DESCRIPTION_COLUMN, place.getDescription());
            upPlaceData.put(Place.ID_COUNTRY_COLUMN, place.getIdCountry());
            upPlaceData.put(Place.ID_PLACE_TYPE_COLUMN, place.getIdPlaceType());
            if (place.getLatitude() != null) {
                upPlaceData.put(Place.LATITUDE_COLUMN, place.getLatitude().toString());
            }
            if (place.getLongitude() != null) {
                upPlaceData.put(Place.LONGITUDE_COLUMN, place.getLongitude().toString());
            }
            database.beginTransaction();
            String idPlaceStr = String.valueOf(idPlace);
            String[] placeIdArray = {idPlaceStr};
            int update = database.update(Place.TABLE_NAME, upPlaceData, Place.ID_PLACE_COLUMN + "=?", placeIdArray);
            if (update > 0) {
                database.delete(Place.IMAGES_FOR_PLACE_TABLE_NAME, Place.ID_PLACE_COLUMN + "=?", placeIdArray);
                for (Integer idImage : place.getImagesIds()) {
                    ContentValues imageCv = new ContentValues(2);
                    imageCv.put(Image.ID_IMAGE_COLUMN, idImage);
                    imageCv.put(Place.ID_PLACE_COLUMN, idPlace);
                    long insert = database.insert(Place.IMAGES_FOR_PLACE_TABLE_NAME, null, imageCv);
                    if (insert == ROW_NOT_INSERTED) {
                        throw new Exception("Не удалось добавить изображение для места с ид: "
                                + idPlace + ", выполняю откат");
                    }
                }
                database.setTransactionSuccessful();
                Log.d(APP_NAME, "Место обновлено с ид: " + idPlace);
            }
        } catch (Exception e) {
            throw new PersistenceException("Не удалось обновить место по ид: " + idPlace);
        } finally {
            if (database != null) {
                database.endTransaction();
            }
        }
    }

    public List<Image> getAllImages() throws PersistenceException {
        List<Image> allImages = new ArrayList<>();
        try {
            try (Cursor cursor = getReadableDatabase().rawQuery("select * from " + Image.TABLE_NAME, null)) {
                if (cursor.moveToFirst()) {
                    do {
                        int idImage = cursor.getInt(cursor.getColumnIndex(Image.ID_IMAGE_COLUMN));
                        byte[] bytes = cursor.getBlob(cursor.getColumnIndex(Image.BINARY_DATA_COLUMN));
                        String url = cursor.getString(cursor.getColumnIndex(Image.URL_COLUMN));
                        allImages.add(new Image(idImage, bytes, url));
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            throw new PersistenceException("Не удалось получить изображения");
        }
        return allImages;
    }
}