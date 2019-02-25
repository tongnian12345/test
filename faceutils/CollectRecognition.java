package com.tenfine.napoleon.faceutils;

import com.baidu.aip.ocr.AipOcr;
import com.tenfine.napoleon.framework.bean.Result;
import com.tenfine.napoleon.framework.bean.ResultCode;
import com.tenfine.napoleon.framework.util.PathStatic;
import com.tenfine.napoleon.user.dao.po.PersonInfo;
import org.json.JSONObject;
import sun.misc.BASE64Encoder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class CollectRecognition {

    private static final String APP_ID = "15443753"; // 百度OCR的APP_ID

    private static final String API_KEY = "i93gPLQev6k3jM6yGgEtL9rH"; // 百度OCR的API_KEY

    private static final String SECRET_KEY = "LpZUrm1NFufZ8Yzf7YgEjjNuI7uuG8fS"; // 百度OCR的SECRET_KEY

    private static final Double sameValue = 20.00; // 人证匹配相似度阈值

    private static final int widthZoom = 900; // 采集照片宽度的最大值

    private static final int heightZoom = 900; // 采集照片高度的最大值

    public static AipOcr client = new AipOcr(APP_ID, API_KEY, SECRET_KEY); // 初始化一个AipOcr

    public static HashMap cacheMap = new HashMap<String, Object>(); // 用于临时数据存储

    private static BASE64Encoder base64Encoder = new BASE64Encoder();


    /**
     * 获取修改分辨率后的采集图片
     * @param srcFile
     * @param cerfNum
     * @return
     * @throws IOException
     */
    public static File getResizeFile(File srcFile, String cerfNum) throws IOException {
        // 开始读取临时图片：srcImg
        Image srcImg = ImageIO.read(srcFile);
        // 读取图片的像素
        BufferedImage bi = (BufferedImage) srcImg;
        float width = bi.getWidth();
        float height = bi.getHeight();
        float scaleWidth = width / widthZoom;
        float scaleHeight = height / heightZoom;
        if (scaleWidth > scaleHeight) {
            width = width / scaleWidth;
            height = height / scaleWidth;
        } else {
            width = width / scaleHeight;
            height = height / scaleHeight;
        }
        String temporaryPath = PathStatic.getTemporaryFilePath();
        BufferedImage buffImg = new BufferedImage((int) width, (int) height, BufferedImage.TYPE_INT_RGB);
        //使用TYPE_INT_RGB修改的图片会变色
        buffImg.getGraphics().drawImage(srcImg.getScaledInstance((int) width, (int) height, Image.SCALE_SMOOTH), 0, 0, null);
        String desPath = temporaryPath + "resizeImage_" + cerfNum + ".jpg";
        File resizeImage = new File(desPath);
        if (resizeImage.exists()) {
            resizeImage.delete();
        }
        ImageIO.write(buffImg, "jpg", resizeImage);
        return resizeImage;
    }


    /**
     * 修改采集图片的分辨率
     * @param bytes 原图片二进制流
     * @param cerfNum
     * @return
     */
    public static File resizeImage(byte[] bytes, String cerfNum) throws IOException {
        String temporaryPath = PathStatic.getTemporaryFilePath();
        File temporaryFolder = new File(temporaryPath);
        if (!temporaryFolder.exists()) { // 创建临时文件夹
            temporaryFolder.mkdirs();
        }
        // 生成临时需要读取的图片：srcFile
        File srcFile = new File(temporaryPath + "read_" + cerfNum + ".jpg");
        if (!srcFile.exists()) {
            srcFile.createNewFile();
        }
        OutputStream os = new FileOutputStream(srcFile);
        os.write(bytes);
        os.close();

        File resizeFile = getResizeFile(srcFile, cerfNum);
        if (srcFile.exists()) { // 图片分辨率修改完成，把原图删除，避免占用空间
            srcFile.delete();
        }
        return resizeFile;
    }


    /**
     * 员工采集接口
     * @param groupId
     * @param cerfNum
     * @param image
     * @return
     */
    public static Result workCollect(String groupId, String cerfNum, File image) {
        String base64 = ImageUtils.imageBase64(image);
        // 人脸检测
        Result faceResult = AttendanceRecognition.faceDetect(base64);
        if (faceResult.getStatus() == ResultCode.ERROR.getCode()) {
            return faceResult;
        }
        String cerfBase64 = (String) cacheMap.get(cerfNum + "front");
        if (null == cerfBase64) {
            return Result.error("人员照片已采集");
        }
        JSONObject object = AttendanceRecognition.faceComparsion(cerfBase64, base64);
        if (object.get("result").equals(null)) {
            return Result.error("照片不合格,请重拍");
        }
        JSONObject result = (JSONObject) object.get("result");
        Double value = result.getDouble("score");
        if (value.compareTo(sameValue) >= 0) { // 照片相似度大于等于指定阈值
            PersonInfo personInfo = (PersonInfo) cacheMap.get(cerfNum);
            // 开始注册人脸
            AttendanceRecognition.addUser(base64, groupId, cerfNum);
            // 清除当前采集人员在内存当中的身份证信息
            cacheMap.remove(cerfNum);
            cacheMap.remove(cerfNum + "front");
            return Result.success(personInfo);
        } else {
            return Result.error("人证不匹配");
        }
    }


    /**
     * 身份证正面识别
     * @param image
     * @return
     */
    public static Result cerfNumFront(byte[] image) {
        String base64 = base64Encoder.encode(image);
        if (AttendanceRecognition.hasFace(base64)) { // 存在人脸
            HashMap options = new HashMap<String, String>();
            options.put("detect_risk", "true"); // 开启身份证风险验证
            JSONObject result = client.idcard(image, "front", options);
            String imageStatus = result.getString("image_status");
            if (imageStatus.equals("normal")) { // 识别成功
                JSONObject wordsResult = result.getJSONObject("words_result");
                if (wordsResult.isNull("公民身份号码") || wordsResult.isNull("姓名") || wordsResult.isNull("出生")) {
                    return Result.error("照片不合格,请重拍");
                }
                if (wordsResult.isNull("性别") || wordsResult.isNull("民族") || wordsResult.isNull("住址")) {
                    return Result.error("照片不合格,请重拍");
                }
                JSONObject returnIdno = wordsResult.getJSONObject("公民身份号码");
                JSONObject returnName = wordsResult.getJSONObject("姓名");
                JSONObject returnBirthday = wordsResult.getJSONObject("出生");
                JSONObject returnSex = wordsResult.getJSONObject("性别");
                JSONObject returnNation = wordsResult.getJSONObject("民族");
                JSONObject returnAddress = wordsResult.getJSONObject("住址");

                String idNo = returnIdno.getString("words");
                String name = returnName.getString("words");
                String address = returnAddress.getString("words");
                String birthday = returnBirthday.getString("words");
                String sex = returnSex.getString("words");
                String nation = returnNation.getString("words");

                PersonInfo personInfo = new PersonInfo();
                personInfo.setIdNo(idNo);
                personInfo.setName(name);
                personInfo.setAddress(address);
                personInfo.setBirthday(birthday);
                personInfo.setSex(sex);
                personInfo.setNation(nation);
                cacheMap.put(idNo, personInfo); // 将身份证相关信息存储到内存当中
                cacheMap.put(idNo + "front", base64); // 将身份证正面图片base64的值写进内存
                Map<String, String> map = new HashMap<>();
                map.put("idNo", idNo);
                return Result.success(map);
            } else {
                return Result.error("照片不合格,请重拍");
            }
        } else {
            return Result.error("照片中没有人脸");
        }

    }


    /**
     * 身份证反面识别
     * @param image
     * @return
     */
    public static Result cerfNumBack(byte[] image, String cerfNum) {
        HashMap options = new HashMap<String, String>();
        options.put("detect_risk", "true"); // 开启身份证风险验证
        JSONObject result = client.idcard(image, "back", options);
        String imageStatus = result.getString("image_status");
        if (imageStatus.equals("normal")) { // 识别成功
            JSONObject wordsResult = result.getJSONObject("words_result");
            if (wordsResult.isNull("签发日期") || wordsResult.isNull("失效日期") || wordsResult.isNull("签发机关")) {
                return Result.error("照片不合格,请重拍");
            }
            String issurDate = wordsResult.getJSONObject("签发日期").getString("words");
            String invalidDate = wordsResult.getJSONObject("失效日期").getString("words");
            String period = issurDate + "-" + invalidDate;
            String provide = wordsResult.getJSONObject("签发机关").getString("words");

            PersonInfo personInfo = (PersonInfo) cacheMap.get(cerfNum);
            personInfo.setPeriod(period);
            personInfo.setProvide(provide);
            cacheMap.put(cerfNum, personInfo); // 重新把身份证信息写进内存
            Map<String, String> map = new HashMap<>();
            map.put("idNo", personInfo.getIdNo());
            return Result.success(map);
        } else {
            return Result.error("照片不合格,请重拍");
        }
    }


}
