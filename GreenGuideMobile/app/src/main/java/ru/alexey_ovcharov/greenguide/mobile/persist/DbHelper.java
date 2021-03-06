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

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ru.alexey_ovcharov.greenguide.mobile.Commons.APP_NAME;

/**
 * Created by Алексей on 23.04.2017.
 */

public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "green_guide_db";
    private static final int VERSION = 1;
    public static final int ROW_NOT_EXIST = -1;
    public static final String SETTINGS_TABLE_NAME = "settings";
    public static final String SETTINGS_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS "
            + SETTINGS_TABLE_NAME + "(name VARCHAR PRIMARY KEY NOT NULL UNIQUE, value TEXT)";
    public static final String DATABASE_ID = "database_id";
    public static final String NAME_COLUMN = "name";
    public static final String VALUE_COLUMN = "value";
    private static volatile DbHelper INSTANCE;

    public static DbHelper getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DbHelper.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DbHelper(context);
                }
            }
        }
        return INSTANCE;
    }

    private DbHelper(Context context) {
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
            SQLiteDatabase database = getWritableDatabase();
            name = name.replaceAll("'", "\"");
            value = value.replaceAll("'", "\"");
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
    public List<PlaceType> getPlacesTypesSorted() {
        Log.d(APP_NAME, "Выбираю типы мест из базы");
        List<PlaceType> placeTypes = Collections.EMPTY_LIST;
        try {
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursor = database.rawQuery(
                    "select * from place_types order by " + PlaceType.TYPE_COLUMN, null)) {
                if (cursor.moveToFirst()) {
                    placeTypes = new ArrayList<>();
                    do {
                        placeTypes.add(new PlaceType(cursor));
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            Log.e(APP_NAME, e.toString(), e);
        }
        Log.d(APP_NAME, "Выбрал типов: " + placeTypes.size());
        return placeTypes;
    }

    public void addPlaceType(String placeType) throws PersistenceException {
        Log.d(APP_NAME, "Создаю новый тип мест: " + placeType);
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(PlaceType.TYPE_COLUMN, placeType);
            values.put(Entity.GUID_COLUMN_NAME, UUID.randomUUID().toString());
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
                        Place place = new Place(cursor);
                        places.add(place);
                        if (withImages) {
                            List<Image> imagesId = getImagesIdsForPlace(place.getIdPlace(), database);
                            place.addImages(imagesId);
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
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursor = database.rawQuery("select count(*) as places_count from "
                    + Place.TABLE_NAME, null)) {
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

    public Map<Integer, String> getCountriesSorted() throws PersistenceException {
        Log.d(APP_NAME, "Получаю список стран");
        try {
            SQLiteDatabase database = getReadableDatabase();
            Map<Integer, String> countries = new HashMap<>();
            try (Cursor cursor = database.rawQuery("select * from " + Country.TABLE_NAME
                    + " order by " + Country.COUNTRY_COLUMN, null)) {
                if (cursor.moveToFirst()) {
                    do {
                        String countryName = cursor.getString(cursor.getColumnIndex(Country.COUNTRY_COLUMN));
                        int idCountry = cursor.getInt(cursor.getColumnIndex(Country.ID_COUNTRY_COLUMN));
                        countries.put(idCountry, countryName);
                    } while (cursor.moveToNext());
                }
            }
            Log.d(APP_NAME, "Найдено стран: " + countries.size());
            return countries;
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось получить список стран", ex);
        }
    }

    public void addCountry(String country) throws PersistenceException {
        Log.d(APP_NAME, "Добавляю страну: " + country);
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(Country.COUNTRY_COLUMN, country.replace("'", "\""));
            long insertRow = database.insert(Country.TABLE_NAME, null, cv);
            if (insertRow != ROW_NOT_EXIST) {
                Log.d(APP_NAME, "Страна добавлена");
            } else {
                throw new SQLException("Ошибка при добавлении страны");
            }
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось добавить страну", ex);
        }
    }

    public Image addImage(@NonNull String imageUrl) throws PersistenceException {
        Log.d(APP_NAME, "Вставляю новое изображение");
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Image.URL_COLUMN, imageUrl);
            String guid = UUID.randomUUID().toString();
            values.put(Entity.GUID_COLUMN_NAME, guid);
            long rowNum = database.insert(Image.TABLE_NAME, null, values);
            if (rowNum != ROW_NOT_EXIST) {
                Log.d(APP_NAME, "Изображение вставлено");
                return new Image((int) rowNum, guid, imageUrl);
            } else {
                throw new SQLException("Не удалось вставить изображение");
            }
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось вставить изображение", ex);
        }
    }

    @NonNull
    public List<Image> getImageData(Collection<? extends Number> idsImage) throws PersistenceException {
        Log.d(APP_NAME, "Получаю данные изображения по ид: " + idsImage);
        try {
            SQLiteDatabase database = getReadableDatabase();
            String inClause = buildInClauseNumber(idsImage);
            List<Image> imageList = new ArrayList<>();
            try (Cursor cursor = database.rawQuery("select * from "
                    + Image.TABLE_NAME + " where "
                    + Image.ID_IMAGE_COLUMN + " in (" + inClause + ")", null)) {
                if (cursor.moveToFirst()) {
                    do {
                        Image image = new Image(cursor);
                        imageList.add(image);
                    } while (cursor.moveToNext());
                }
            }
            Log.d(APP_NAME, "Выбрано изображений: " + imageList.size());
            return imageList;
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось получить изображения", ex);
        }
    }

    @NonNull
    private static String buildInClauseNumber(Collection<? extends Number> ids) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Number id : ids) {
            if (!first) {
                builder.append(",");
            } else {
                first = false;
            }
            builder.append(id);
        }
        return builder.toString();
    }

    @NonNull
    private static String buildInClauseString(Collection<String> ids) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String id : ids) {
            if (!first) {
                builder.append(",");
            } else {
                first = false;
            }
            builder.append("'").append(id.replace("'", "\"")).append("'");
        }
        return builder.toString();
    }


    public void addPlace(@NonNull Place place) throws PersistenceException {
        Log.d(APP_NAME, "Добавляю новое место: " + place);
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Place.ADDRESS_COLUMN, place.getAddress());
            values.put(Place.DATE_CREATE_COLUMN, place.getDateCreate());
            values.put(Place.DESCRIPTION_COLUMN, place.getDescription());
            values.put(Place.ID_COUNTRY_COLUMN, place.getIdCountry());
            values.put(Place.ID_PLACE_TYPE_COLUMN, place.getIdPlaceType());
            values.put(Entity.GUID_COLUMN_NAME, UUID.randomUUID().toString());
            if (place.getLatitude() != null) {
                values.put(Place.LATITUDE_COLUMN, place.getLatitude().toString());
            }
            if (place.getLongitude() != null) {
                values.put(Place.LONGITUDE_COLUMN, place.getLongitude().toString());
            }
            List<Image> imagesIds = place.getImagesInfo();
            database.beginTransaction();
            try {
                long idPlace = database.insert(Place.TABLE_NAME, null, values);
                if (idPlace != ROW_NOT_EXIST) {
                    for (Image image : imagesIds) {
                        ContentValues imageCv = new ContentValues(2);
                        imageCv.put(Image.ID_IMAGE_COLUMN, image.getIdImage());
                        imageCv.put(Place.ID_PLACE_COLUMN, idPlace);
                        long insert = database.insert(Place.IMAGES_FOR_PLACE_TABLE_NAME, null, imageCv);
                        if (insert == ROW_NOT_EXIST) {
                            throw new Exception("Не удалось добавить изображение для места с ид: "
                                    + idPlace + ", выполняю откат");
                        }
                    }
                    database.setTransactionSuccessful();
                    Log.d(APP_NAME, "Место добавлено, ид: " + idPlace);
                }
            } finally {
                database.endTransaction();
            }
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось добавить место", ex);
        }
    }

    public Place getPlaceWithImages(int placeId) throws PersistenceException {
        Log.d(APP_NAME, "Выбираю место с изображениями, ид: " + placeId);
        try {
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursorPlace = database.rawQuery("select *" +
                    " from places where id_place = " + placeId, null)) {
                if (cursorPlace.moveToFirst()) {
                    Place place = new Place(cursorPlace);
                    List<Image> images = getImagesIdsForPlace(placeId, database);
                    place.addImages(images);
                    return place;
                } else {
                    throw new PersistenceException("Не найдено место с ид: " + placeId);
                }

            }
        } catch (Exception e) {
            throw new PersistenceException("Не удалось получить информацию " +
                    "для места с ид: " + placeId, e);
        }
    }

    @NonNull
    private static List<Image> getImagesIdsForPlace(int placeId, SQLiteDatabase database) {
        List<Image> images = new ArrayList<>();
        try (Cursor cursorImages = database.rawQuery("select i.* "
                + " from  " + Image.TABLE_NAME + " i"
                + " join " + Place.IMAGES_FOR_PLACE_TABLE_NAME + " ifp on i."
                + Image.ID_IMAGE_COLUMN + "= ifp." + Image.ID_IMAGE_COLUMN +
                " where id_place = " + placeId, null)) {
            if (cursorImages.moveToFirst()) {
                do {
                    images.add(new Image(cursorImages));
                } while (cursorImages.moveToNext());
            }
        }
        Log.d(APP_NAME, "Выбрано изображений: " + images.size());
        return images;
    }

    public void deletePlaceById(int idPlace) throws PersistenceException {
        Log.d(APP_NAME, "Удаляю место с ид: " + idPlace);
        SQLiteDatabase database = null;
        try {
            database = getWritableDatabase();
            database.beginTransaction();
            String[] whereArgs = {String.valueOf(idPlace)};
            int deleteIfpResult = database.delete(Place.IMAGES_FOR_PLACE_TABLE_NAME,
                    Place.ID_PLACE_COLUMN + "=?", whereArgs);
            if (deleteIfpResult > 0) {
                int deleteResult = database.delete(Place.TABLE_NAME, Place.ID_PLACE_COLUMN + "=?", whereArgs);
                if (deleteResult > 0) {
                    database.setTransactionSuccessful();
                    Log.d(APP_NAME, "Удалено успешно");
                    return;
                }
            }
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось удалить место с ид: " + idPlace, ex);
        } finally {
            if (database != null) {
                database.endTransaction();
            }
        }
    }

    public void deleteThingById(int idThing) throws PersistenceException {
        Log.d(APP_NAME, "Удаляю объект энциклопедии по ид: " + idThing);
        SQLiteDatabase database = null;
        try {
            database = getWritableDatabase();
            database.beginTransaction();
            String[] whereArgs = {String.valueOf(idThing)};
            int deleteIfpResult = database.delete(Thing.IMAGE_FOR_THING_TABLE_NAME,
                    Thing.ID_THING_COLUMN + "=?", whereArgs);
            if (deleteIfpResult > 0) {
                int deleteResult = database.delete(Thing.TABLE_NAME, Thing.ID_THING_COLUMN + "=?", whereArgs);
                if (deleteResult > 0) {
                    database.setTransactionSuccessful();
                    Log.d(APP_NAME, "Удалено успешно");
                    return;
                }
            }
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось удалить объект с ид: " + idThing, ex);
        } finally {
            if (database != null) {
                database.endTransaction();
            }
        }
    }

    public void updatePlace(@NonNull Place place) throws PersistenceException {
        int idPlace = place.getIdPlace();
        try {
            SQLiteDatabase database = getWritableDatabase();
            Log.d(APP_NAME, "Обновляю место по ид: " + idPlace);
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
            try {
                String idPlaceStr = String.valueOf(idPlace);
                String[] placeIdArray = {idPlaceStr};
                int update = database.update(Place.TABLE_NAME, upPlaceData, Place.ID_PLACE_COLUMN + "=?", placeIdArray);
                if (update > 0) {
                    database.delete(Place.IMAGES_FOR_PLACE_TABLE_NAME, Place.ID_PLACE_COLUMN + "=?", placeIdArray);
                    for (Image image : place.getImagesInfo()) {
                        ContentValues imageCv = new ContentValues(2);
                        imageCv.put(Image.ID_IMAGE_COLUMN, image.getIdImage());
                        imageCv.put(Place.ID_PLACE_COLUMN, idPlace);
                        long insert = database.insert(Place.IMAGES_FOR_PLACE_TABLE_NAME, null, imageCv);
                        if (insert == ROW_NOT_EXIST) {
                            throw new Exception("Не удалось добавить изображение для места с ид: "
                                    + idPlace + ", выполняю откат");
                        }
                    }
                    database.setTransactionSuccessful();
                    Log.d(APP_NAME, "Место обновлено с ид: " + idPlace);
                }
            } finally {
                database.endTransaction();
            }

        } catch (Exception e) {
            throw new PersistenceException("Не удалось обновить место по ид: " + idPlace);
        }
    }

//    @NonNull
//    public List<Image> getAllImages() throws PersistenceException {
//        List<Image> allImages = new ArrayList<>();
//        try {
//            try (Cursor cursor = getReadableDatabase().rawQuery("select * from " + Image.TABLE_NAME, null)) {
//                if (cursor.moveToFirst()) {
//                    do {
//                        allImages.add(new Image(cursor));
//                    } while (cursor.moveToNext());
//                }
//            }
//        } catch (Exception e) {
//            throw new PersistenceException("Не удалось получить изображения");
//        }
//        return allImages;
//    }

    @NonNull
    public LatLng getLastKnownCoordinates() {
        return new LatLng(53.36056, 83.76361);
    }

    public void saveCategoryOfThing(String refName) throws PersistenceException {
        Log.d(APP_NAME, "Добавляю новый справочник/категорию вещей: " + refName);
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(CategoryOfThing.CATEGORY_COLUMN, refName);
            cv.put(Entity.GUID_COLUMN_NAME, UUID.randomUUID().toString());
            long insert = database.insert(CategoryOfThing.TABLE_NAME, null, cv);
            if (insert > 0) {
                Log.d(APP_NAME, "Справочник/категория вещей добавлены");
            }
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось создать справочник/категорию вещей", ex);
        }
    }

    public List<CategoryOfThing> getAllCategoryOfThingsSorted() throws PersistenceException {
        Log.d(APP_NAME, "Выбираю справочники/категории вещей");
        try {
            SQLiteDatabase database = getReadableDatabase();
            List<CategoryOfThing> categoryOfThings;
            try (Cursor cursor = database.rawQuery("select * from " + CategoryOfThing.TABLE_NAME
                    + " order by " + CategoryOfThing.CATEGORY_COLUMN, null)) {
                categoryOfThings = new ArrayList<>();
                if (cursor.moveToFirst()) {
                    do {
                        categoryOfThings.add(new CategoryOfThing(cursor));
                    } while (cursor.moveToNext());
                }
            }
            Log.d(APP_NAME, "Выбрано справочников/категорий: " + categoryOfThings.size());
            return categoryOfThings;
        } catch (Exception ex) {
            throw new PersistenceException("Не удалось получить справочники/категории вещей", ex);
        }
    }

    @NonNull
    public Set<Image> getImagesByGuids(@NonNull List<String> guids) throws PersistenceException {
        Log.d(APP_NAME, "Выбираю изображения по глобальным идентификаторам");
        try {
            SQLiteDatabase database = getReadableDatabase();
            String guidsAsString = buildInClauseString(guids);
            Cursor cursor = database.rawQuery("select * from " + Image.TABLE_NAME + " where "
                    + Entity.GUID_COLUMN_NAME + " in (" + guidsAsString + ")", null);
            Set<Image> result = new HashSet<>();
            if (cursor.moveToFirst()) {
                do {
                    Image image = new Image(cursor);
                    result.add(image);
                } while (cursor.moveToNext());
            }
            Log.d(APP_NAME, "Выбрано изображений: " + result.size());
            return result;
        } catch (Exception e) {
            throw new PersistenceException("Не удалось получить изображения", e);
        }
    }

    public Set<Image> createImages(@NonNull Map<String, String> guidesAndUrls) throws PersistenceException {
        Log.d(APP_NAME, "Сохраняю набор изображений, количество: " + guidesAndUrls.size());
        SQLiteDatabase database = null;
        try {
            database = getWritableDatabase();
            database.beginTransaction();
            Set<Image> images = new HashSet<>(guidesAndUrls.size());
            for (Map.Entry<String, String> entry : guidesAndUrls.entrySet()) {
                String guid = entry.getKey();
                String url = entry.getValue();
                ContentValues cv = new ContentValues();
                cv.put(Entity.GUID_COLUMN_NAME, guid);
                cv.put(Image.URL_COLUMN, url);
                long rowInsertNumber = database.insert(Image.TABLE_NAME, null, cv);
                if (rowInsertNumber == ROW_NOT_EXIST) {
                    throw new SQLException("Вставка изображения не удалась: " + url);
                }
                images.add(new Image((int) rowInsertNumber, guid, url));
            }
            database.setTransactionSuccessful();
            Log.d(APP_NAME, "Сохранено");
            return images;
        } catch (Exception e) {
            throw new PersistenceException("Не удалось набор изображений", e);
        } finally {
            if (database != null) {
                database.endTransaction();
            }
        }
    }

    public int getIdPlaceByGuid(String placeGuid) throws PersistenceException {
        UUID.fromString(placeGuid);
        try {
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursor = database.rawQuery("select "
                    + Place.ID_PLACE_COLUMN + " from "
                    + Place.TABLE_NAME + " where "
                    + Entity.GUID_COLUMN_NAME + "='" + placeGuid + "'", null)) {
                int result;
                if (cursor.moveToFirst()) {
                    result = cursor.getInt(cursor.getColumnIndex(Place.ID_PLACE_COLUMN));
                } else {
                    result = ROW_NOT_EXIST;
                }
                return result;
            }
        } catch (Exception e) {
            throw new PersistenceException("Ошибка поиска изображения", e);
        }
    }

    @NonNull
    public List<Thing> getThingsByCategory(int idCategory) throws PersistenceException {
        Log.d(APP_NAME, "Выбираю объекты из энциклопедии для категории: " + idCategory);
        try {
            SQLiteDatabase database = getReadableDatabase();
            try (Cursor cursor = database.rawQuery("select " +
                    "* from " + Thing.TABLE_NAME + " where "
                    + Thing.ID_CATEGORY_COLUMN + "=" + idCategory, null)) {
                List<Thing> result = new ArrayList<>();
                if (cursor.moveToFirst()) {
                    do {
                        Thing thing = new Thing(cursor);
                        try (Cursor cursorImg = database.rawQuery("select i.* " +
                                " from " + Image.TABLE_NAME + " i"
                                + " join " + Thing.IMAGE_FOR_THING_TABLE_NAME + " ift " +
                                " on ift." + Image.ID_IMAGE_COLUMN + "=" + "i." + Image.ID_IMAGE_COLUMN
                                + " where " + Thing.ID_THING_COLUMN + "=" + thing.getIdThing(), null)) {
                            if (cursorImg.moveToFirst()) {
                                do {
                                    thing.addImage(new Image(cursorImg));
                                } while (cursorImg.moveToNext());
                            }
                        }
                        result.add(thing);
                    } while (cursor.moveToNext());

                }
                Log.d(APP_NAME, "Выбрано объектов: " + result.size());
                return result;
            }
        } catch (Exception e) {
            throw new PersistenceException("Ошибка при получении объектов энциклопедии", e);
        }
    }

    public void deleteCategory(CategoryOfThing categoryOfThing) throws PersistenceException {
        SQLiteDatabase database = null;
        try {
            database = getWritableDatabase();
            database.beginTransaction();
            String[] whereArgs = {String.valueOf(categoryOfThing.getIdCategory())};
            database.delete(Thing.TABLE_NAME, Thing.ID_CATEGORY_COLUMN + "=?", whereArgs);
            int delete = database.delete(CategoryOfThing.TABLE_NAME,
                    CategoryOfThing.ID_CATEGORY_COLUMN + "=?", whereArgs);
            if (delete > 0) {
                Log.d(APP_NAME, "Успешно удалено");
                database.setTransactionSuccessful();
            } else {
                throw new SQLException("Не удалось удалить категорияю");
            }
        } catch (Exception ex) {
            throw new PersistenceException("Ошибка при удалении категорий", ex);
        } finally {
            if (database != null) {
                database.endTransaction();
            }
        }
    }

    public void addThing(@NonNull Thing thing) throws PersistenceException {
        Log.d(APP_NAME, "Добавляю объект энциклопедии: " + thing);
        SQLiteDatabase database = null;
        try {
            database = getWritableDatabase();
            database.beginTransaction();
            ContentValues cv = new ContentValues();
            cv.put(Thing.NAME_COLUMN, thing.getName());
            cv.put(Thing.DESCRIPTION_COLUMN, thing.getDescription());
            cv.put(Thing.DANGER_FOR_ENVIRONMENT_COLUMN, thing.getDangerForEnvironment());
            cv.put(Thing.DECOMPOSITION_TIME_COLUMN, thing.getDecompositionTime());
            cv.put(Thing.ID_CATEGORY_COLUMN, thing.getIdCategory());
            cv.put(Thing.ID_COUNTRY_COLUMN, thing.getIdCountry());
            cv.put(Entity.GUID_COLUMN_NAME, UUID.randomUUID().toString());
            long insert = database.insert(Thing.TABLE_NAME, null, cv);
            if (insert != ROW_NOT_EXIST) {
                ContentValues cv1 = new ContentValues();
                cv1.put(Thing.ID_THING_COLUMN, insert);
                cv1.put(Image.ID_IMAGE_COLUMN, thing.getFirstImage().getIdImage());
                long insertImg = database.insert(Thing.IMAGE_FOR_THING_TABLE_NAME, null, cv1);
                if (insertImg != ROW_NOT_EXIST) {
                    database.setTransactionSuccessful();
                    thing.setIdThing((int) insert);
                    Log.d(APP_NAME, "Объект добавлен");
                } else {
                    throw new SQLException("Ошибка вставки для объекта энциклопедии изображения");
                }
            } else {
                throw new SQLException("Ошибка вставки в БД объекта энциклопедии");
            }
        } catch (Exception e) {
            throw new PersistenceException("Ошибка при сохранении объектов энциклопедии", e);
        } finally {
            if (database != null) {
                database.endTransaction();
            }
        }
    }

    public void addNote(Note note) throws PersistenceException {

    }

    public void addNoteType(NoteType noteType) throws PersistenceException {

    }

    public List<NoteType> getNoteTypesSorted() throws PersistenceException {
        return Collections.EMPTY_LIST;
    }

    public List<Note> getNotesByType(int idNoteType) throws PersistenceException {
        return Collections.EMPTY_LIST;
    }
}