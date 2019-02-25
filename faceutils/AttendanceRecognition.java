package com.tenfine.napoleon.faceutils;

import com.baidu.aip.face.AipFace;
import com.baidu.aip.face.FaceVerifyRequest;
import com.baidu.aip.face.MatchRequest;
import com.tenfine.napoleon.framework.bean.Result;
import org.json.JSONArray;
import org.json.JSONObject;
import sun.misc.BASE64Encoder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AttendanceRecognition {

    private static final String APP_ID = "15424108"; // 百度人脸识别的APP_ID

    private static final String API_KEY = "ViATpaZIiyZtZsVI2N8QvEZP"; // 百度人脸识别的API_KEY

    private static final String SECRET_KEY = "7GsOeAYG1I6AVRKGtkqkAM3we0BCoVSR"; // 百度人脸识别的SECRET_KEY

    private static final Double realValue = 0.80; // 人脸检测活体检测阈值

    private static final Double angleValue = 30.00; // 人脸图像俯仰角，滚转角，偏航角度阈值

    private static final Double blurValue = 0.4; // 人脸照片模糊度阈值

    private static final Double distanceValue = 60.00; // 人脸照片两眼之间的距离阈值

    private static final Double matchValue = 85.00; // 人脸搜索最高匹配度用户的阈值

    // 初始化一个AipFace
    public static AipFace client = new AipFace(APP_ID, API_KEY, SECRET_KEY);


    /**
     * 创建用户组
     * @param groupId
     * @return
     */
    public static Result createUserGroup(String groupId) {
        HashMap options = new HashMap<String, String>();
        JSONObject result = client.groupAdd(groupId, options);
        if (result.getInt("error_code") == 0) {
            return Result.success(null);
        } else {
            return Result.error("创建用户组失败");
        }
    }


    /**
     * 人脸注册
     * @param base64
     * @param groupId
     * @param userId
     */
    public static void addUser(String base64, String groupId, String userId) {
        HashMap<String, String> options = new HashMap();
        client.addUser(base64, "BASE64", groupId, userId, options);
    }


    /**
     * 人脸检测
     * @param base64
     * @return
     */
    public static Result faceDetect(String base64) {
        HashMap<String, String> options = new HashMap<>();
        options.put("face_field", "quality,landmark"); // 返回人脸质量信息和4个关键位置
        options.put("face_type", "LIVE"); // 控制人脸的类型为：LIVE生活照
        JSONObject object = client.detect(base64, "BASE64", options);
        if (object.getString("error_msg").equals("pic not has face")) {
            return Result.error("照片中没有人脸");
        }
        JSONObject detectResult = object.getJSONObject("result");
        JSONObject faceList = detectResult.getJSONArray("face_list").getJSONObject(0);
        JSONObject angle = faceList.getJSONObject("angle"); // 人脸角度值
        JSONObject quality = faceList.getJSONObject("quality"); // 人脸质量
        Double blur = quality.getDouble("blur"); // 清晰度
        Double yaw = angle.getDouble("yaw");
        Double pitch = angle.getDouble("pitch");
        Double roll = angle.getDouble("roll");
        if (yaw.compareTo(angleValue) >= 0 || pitch.compareTo(angleValue) >= 0 || roll.compareTo(angleValue) >= 0) { // 人脸图像角度大于阈值
            return Result.error("请摆正人脸后重拍");
        }
        if (blur.compareTo(blurValue) >= 0) {
            return Result.error("照片太模糊,请重拍");
        }
        // 已知两点的坐标，计算两点之间的距离
        Double x0 = faceList.getJSONArray("landmark").getJSONObject(0).getDouble("x");
        Double x1 = faceList.getJSONArray("landmark").getJSONObject(1).getDouble("x");
        Double y0 = faceList.getJSONArray("landmark").getJSONObject(0).getDouble("y");
        Double y1 = faceList.getJSONArray("landmark").getJSONObject(1).getDouble("y");
        Double distance = Math.sqrt(Math.pow(x1 - x0, 2) - Math.pow(y1 - y0, 2)); // 计算两眼之间的距离
        if (distance <= distanceValue) {
            return Result.error("请靠近摄像头后重拍");
        }
        return Result.success();
    }


    /**
     * 考勤功能
     * @param image
     * @param groupId
     * @return
     */
    public static Result workerAttendance(byte[] image, String groupId) {
        String base64 = new BASE64Encoder().encode(image);
        HashMap options = new HashMap<String, String>();
        options.put("liveness_control", "NORMAL"); // 活体检测控制
        JSONObject result = client.search(base64, "BASE64", groupId, options);
        if (result.getString("error_msg").equals("pic not has face")) {
            return Result.error("照片中没有人脸");
        }
        if (result.getString("error_msg").equals("match user is not found")) {
            return Result.error("人员未采集");
        }
        if (result.get("result").equals(null)) {
            return Result.error("照片不合格,请重拍");
        }
        JSONObject object = (JSONObject) result.get("result");
        JSONArray users = (JSONArray) object.get("user_list"); // 用户列表
        JSONObject userList = (JSONObject) users.get(0);
        Double score = userList.getDouble("score"); // 人脸搜索匹配度
        if (score.compareTo(matchValue) >= 0) { // 人脸搜索符合预期
            String idNo = userList.getString("user_id");
            Map<String, String> map = new HashMap<>();
            map.put("idNo", idNo);
            map.put("score", score.toString());
            map.put("position", "待开发");
            return Result.success(map);
        } else {
            return Result.error("人员未采集");
        }
    }


    /**
     * 通过图片Base64编码检测图片中是否存在人脸
     * @param imageBase64
     * @return
     */
    public static boolean hasFace(String imageBase64) {
        HashMap<String, String> options = new HashMap<>();
        JSONObject result = client.detect(imageBase64, "BASE64", options);
        Object object = result.get("result");
        if (object.equals(null)) {
            return false;
        }else {
            return true;
        }
    }


    /**
     * 对比两张照片的人脸信息
     * @param image1
     * @param image2
     * @return
     */
    public static JSONObject faceComparsion(String image1, String image2) {
        MatchRequest request1 = new MatchRequest(image1, "BASE64");
        MatchRequest request2 = new MatchRequest(image2, "BASE64");
        ArrayList<MatchRequest> requests = new ArrayList<>();
        requests.add(request1);
        requests.add(request2);
        return client.match(requests);
    }


}
