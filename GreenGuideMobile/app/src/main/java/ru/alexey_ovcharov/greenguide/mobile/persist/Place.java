package ru.alexey_ovcharov.greenguide.mobile.persist;


import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import ru.alexey_ovcharov.greenguide.mobile.Commons;

/**
 * Created by Алексей on 23.04.2017.
 */
@Entity
public class Place {

    public static final String ID_PLACE_COLUMN = "id_place";
    public static final String DESCRIPTION_COLUMN = "description";
    public static final String LATITUDE_COLUMN = "latitude";
    public static final String LONGITUDE_COLUMN = "longitude";
    public static final String ADDRESS_COLUMN = "address";
    public static final String DATE_CREATE_COLUMN = "date_create";
    public static final String ID_PLACE_TYPE_COLUMN = "id_place_type";
    public static final String ID_COUNTRY_COLUMN = "id_country";
    public static final String TABLE_NAME = "places";

    public static final String DROP_SCRIPT = "DROP TABLE IF EXISTS " + TABLE_NAME;
    public static final String CREATE_SCRIPT = "CREATE TABLE " + TABLE_NAME
            + " (" + ID_PLACE_COLUMN + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            DESCRIPTION_COLUMN + " TEXT, " +
            LATITUDE_COLUMN + " DECIMAL (15, 6), " +
            LONGITUDE_COLUMN + " DECIMAL (15, 6), " +
            ADDRESS_COLUMN + " VARCHAR (100), " +
            DATE_CREATE_COLUMN + " DATETIME, " +
            ID_PLACE_TYPE_COLUMN + " INTEGER NOT NULL REFERENCES " +
            "       place_types (id_place_type) ON DELETE RESTRICT ON UPDATE CASCADE, " +
            ID_COUNTRY_COLUMN + " INTEGER REFERENCES countries (id_country) " +
            "   ON DELETE RESTRICT ON UPDATE CASCADE);";

    public static final String IMAGES_FOR_PLACE_TABLE_NAME = "images_for_place";
    public static final String ID_IMAGE_FOR_PLACE_COLUMN = "id_image_for_place";
    public static final String IMAGE_FOR_PLACES_DROP_SCRIPT = "DROP TABLE IF EXISTS " + IMAGES_FOR_PLACE_TABLE_NAME;
    public static final String IMAGE_FOR_PLACES_CREATE_SCRIPT = "CREATE TABLE "
            + IMAGES_FOR_PLACE_TABLE_NAME + " (" +
            ID_IMAGE_FOR_PLACE_COLUMN + " INTEGER PRIMARY KEY NOT NULL, " +
            ID_PLACE_COLUMN + " INTEGER NOT NULL " +
//            "REFERENCES places (id_place) " +
//            "   ON DELETE RESTRICT ON UPDATE CASCADE, " +
            "," +
            Image.ID_IMAGE_COLUMN + " INTEGER NOT NULL " +
//            "REFERENCES images (id_image)" +
//            " ON DELETE RESTRICT ON UPDATE CASCADE" +
            ");";

    private int idPlace;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private String dateCreate;
    private int idPlaceType;
    private Integer idCountry;
    private List<Integer> imagesInfo = new ArrayList<>(1);

    public List<Integer> getImagesIds() {
        return imagesInfo;
    }

    public void addImageId(int idImage) {
        imagesInfo.add(idImage);
    }

    public Place() {

    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getDateCreate() {
        return dateCreate;
    }

    @Override
    public String toString() {
        return "Place{" +
                "idPlace=" + idPlace +
                ", description='" + description + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", address='" + address + '\'' +
                ", dateCreate='" + dateCreate + '\'' +
                ", idPlaceType=" + idPlaceType +
                ", idCountry=" + idCountry +
                ", imagesInfo=" + imagesInfo +
                '}';
    }

    public void setDateCreate(String dateYYYY_MM_DD) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(Commons.SQL_DATE_FORMAT);
        dateFormat.setLenient(true);
        dateFormat.parse(dateYYYY_MM_DD);
        this.dateCreate = dateYYYY_MM_DD;
    }

    public void setDateCreate(Date dateCreate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(Commons.SQL_DATE_FORMAT);
        this.dateCreate = dateFormat.format(dateCreate);
    }

    public int getIdPlace() {
        return idPlace;
    }

    public void setIdPlace(int idPlace) {
        this.idPlace = idPlace;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getIdCountry() {
        return idCountry;
    }

    public void setIdCountry(Integer idCountry) {
        this.idCountry = idCountry;
    }

    public int getIdPlaceType() {
        return idPlaceType;
    }

    public void setIdPlaceType(int idPlaceType) {
        this.idPlaceType = idPlaceType;
    }

    public void addImageInfo(int idImage) {
        this.imagesInfo.add(idImage);
    }

    public JSONObject toJsonObject(Map<Integer, String> countries, Context context)
            throws JSONException, FileNotFoundException {

        JSONObject jsonObject = new JSONObject();
        jsonObject.put(DESCRIPTION_COLUMN, description);
        jsonObject.put(ADDRESS_COLUMN, address);
        jsonObject.put(Country.COUNTRY_COLUMN, countries.get(idCountry));
        jsonObject.put(LATITUDE_COLUMN, latitude);
        jsonObject.put(LONGITUDE_COLUMN, longitude);
        jsonObject.put(DATE_CREATE_COLUMN, dateCreate);
        jsonObject.put(ID_PLACE_TYPE_COLUMN, idPlaceType);
        JSONArray imagesJsonArray = new JSONArray();
        for (Integer imageId : imagesInfo) {
            imagesJsonArray.put(imageId);
        }
        jsonObject.put("images", imagesJsonArray);
        return jsonObject;
    }

    @NonNull
    public List<Image.ImageDataWrapper<Bitmap>> getImagesBitmaps(DbHelper dbHelper, ContentResolver contentResolver) {
        try {
            List<Image.ImageDataWrapper<Bitmap>> imageDataWrappers = new ArrayList<>(imagesInfo.size());
            List<String> imageIds = new ArrayList<>();
            boolean success = imagesInfo.size() > 0;
            for (Integer idImage : imagesInfo) {
                try {
                    List<Image> imageData = dbHelper.getImageData(Collections.singletonList(String.valueOf(idImage)));
                    Image image = imageData.get(0);
                    if (image.getUrl() != null) {
                        Uri imageUrl = Uri.parse(image.getUrl());
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUrl);
                        imageDataWrappers.add(new Image.ImageDataWrapper(idImage, bitmap));
                    } else if (image.getBinaryData() != null) {
                        byte[] binaryData = image.getBinaryData();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(binaryData, 0, binaryData.length);
                        imageDataWrappers.add(new Image.ImageDataWrapper(image.getIdImage(), bitmap));
                    }
                } catch (Exception e) {
                    success = false;
                    Log.e(Commons.APP_NAME, e.toString(), e);
                }
            }
            return imageDataWrappers;
        } catch (Exception e) {
            Log.e(Commons.APP_NAME, e.toString(), e);
        }
        return Collections.EMPTY_LIST;
    }

    public void addImageIds(List<Integer> imagesId) {
        imagesInfo.addAll(imagesId);
    }
}