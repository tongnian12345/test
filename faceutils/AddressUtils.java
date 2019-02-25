package com.tenfine.napoleon.faceutils;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AddressUtils {

    private static final String AK = "hLhOCPgfLkjMO8Qkvp6kref6CUc9PLx6";

    /**
     * 通过经纬度获取中文详细地址信息
     * @param latitude
     * @param longitude
     * @return
     */
    public static String getAddressInfo(String latitude, String longitude) {
        String address;
        try {
            URL url = new URL("http://api.map.baidu.com/geocoder/v2/?callback=renderReverse&location="+latitude+","+longitude+"&output=json&pois=1&ak="+AK);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();

            InputStream input = urlConnection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            String str = reader.readLine();
            str = str.substring(str.indexOf("(") + 1, str.length() - 1);
            JSONObject object = new JSONObject(str);
            address = object.getJSONObject("result").getString("formatted_address");

            input.close();
            reader.close();
        } catch (Exception e) {
            address = "获取地理位置信息失败，请重新尝试";
            e.printStackTrace();
        }
        return address;
    }

}
