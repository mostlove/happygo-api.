package com.magicbeans.happygo.controller;


import com.magicbeans.base.ajax.ResponseData;
import com.magicbeans.happygo.controller.base.BaseController;
import com.magicbeans.happygo.entity.User;
import com.magicbeans.happygo.exception.InterfaceCommonException;
import com.magicbeans.happygo.redis.RedisService;
import com.magicbeans.happygo.service.IIncomeDetailService;
import com.magicbeans.happygo.service.IUserService;
import com.magicbeans.happygo.sms.SMSCode;
import com.magicbeans.happygo.util.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.MessageFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author magic-beans
 * @since 2017-07-28
 */
@RestController
@RequestMapping("/user")
@Api(description = "用户管理接口")
public class UserController extends BaseController {

    @Resource
    private IUserService userService;
    @Resource
    private RedisService redisService;
    @Resource
    private IIncomeDetailService incomeDetailService;



    @RequestMapping(value = "/applyAgent",method = RequestMethod.POST)
    @ApiOperation(value = "申请成为代理商")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "realName",value = "姓名",required = true),
            @ApiImplicitParam(name = "businessPhone",value = "电话",required = true),
            @ApiImplicitParam(name = "idCard",value = "身份证",required = true),
            @ApiImplicitParam(name = "idNumberAttachment",value = "身份证附件",required = true),
            @ApiImplicitParam(name = "legalPerson",value = "法人代表"),
            @ApiImplicitParam(name = "businessLicenseNumber",value = "营业执照号码"),
            @ApiImplicitParam(name = "businessLicenseImg",value = "营业执照凭证"),
            @ApiImplicitParam(name = "applyReason",value = "申请原因",required = true)
    })
    public ResponseData applyAgent(String realName,String businessPhone,String idCard,String idNumberAttachment,
                                   String legalPerson,String businessLicenseNumber,String businessLicenseImg,
                                   String applyReason){

        if(CommonUtil.isEmpty(realName,businessPhone,idCard,idNumberAttachment,applyReason)){
            return buildFailureJson(StatusConstant.FIELD_NOT_NULL,"参数不能为空");
        }
        try {
            User currentUser = LoginHelper.getCurrentUser(redisService);
            // 代理商申请
            User agent = new User();
            agent.setId(currentUser.getId());
            agent.setRealName(realName);
            agent.setBusinessPhone(businessPhone);
            agent.setIdNumber(idCard);
            agent.setIdNumberAttachment(idNumberAttachment);
            agent.setLegalPerson(legalPerson);
            agent.setBusinessLicenseNumber(businessLicenseNumber);
            agent.setBusinessLicenseNumber(businessLicenseNumber);
            agent.setBusinessLicenseImg(businessLicenseImg);
            agent.setApplyReason(applyReason);
            agent.setBusinessStatus(0);
            userService.update(agent);
            // 更新成功后，更新缓存
            User sql = userService.find("id", agent.getId());
            redisService.set(currentUser.getToken(),sql,StatusConstant.LOGIN_VALID,TimeUnit.DAYS);
        } catch (InterfaceCommonException e) {
            return buildFailureJson(e.getErrorCode(),e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return buildFailureJson(StatusConstant.Fail_CODE,"申请失败");
        }
        return buildSuccessCodeJson(StatusConstant.SUCCESS_CODE,"申请成功");
    }


    @RequestMapping(value = "/sendCode",method = RequestMethod.POST)
    @ApiOperation(value = "注册发送验证码",notes = "验证码的正确性由服务端验证，移动端暂不用验证 ")
    @ApiImplicitParam(name = "phone",value = "手机号码" ,required = true)
    public ResponseData sendMsg(String phone){

        if(CommonUtil.isEmpty(phone)){
            return buildFailureJson(StatusConstant.FIELD_NOT_NULL,"参数不能为空");
        }
        User user = userService.getUserByPhone(phone);
        if(null != user && !CommonUtil.isEmpty(user.getPwd())){
            return buildFailureJson(StatusConstant.OBJECT_EXIST,"手机号已经存在");
        }
        String code = SMSCode.createRandomCode();
        String msg = MessageFormat.format(TextMessage.MSG_CODE, code);
        boolean isSuccess = SMSCode.sendMessage(msg, phone);
        if(!isSuccess){
            return buildFailureJson(StatusConstant.Fail_CODE,"发送失败");
        }
        redisService.set(TextMessage.REDIS_KEY_PREFIX + phone,code,TextMessage.EXPIRE_TIME, TimeUnit.MINUTES);
        return buildSuccessJson(StatusConstant.SUCCESS_CODE,"发送成功",code);
    }


    @RequestMapping(value = "/register",method = RequestMethod.POST)
    @ApiOperation(value = "注册")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "phone",value = "手机号",required = true),
            @ApiImplicitParam(name = "code",value = "验证码",required = true),
            @ApiImplicitParam(name = "pwd",value = "加过密的文本",required = true),
            @ApiImplicitParam(name = "deviceType",value = "设备类型，0:android  1:ios 其他不传"),
            @ApiImplicitParam(name = "deviceToken",value = "设备请求的推送token")
    })
    public ResponseData register(String phone,String code,String pwd,
                                 String deviceToken,Integer deviceType){
        if(CommonUtil.isEmpty(phone,code,pwd)){
            return buildFailureJson(StatusConstant.FIELD_NOT_NULL,"参数不能为空");
        }
        Object o = redisService.get(TextMessage.REDIS_KEY_PREFIX + phone);
        if(null == o || !code.equals(o.toString())){
            return buildFailureJson(StatusConstant.Fail_CODE,"验证码失效");
        }
        User user = userService.getUserByPhone(phone);
        if(null != user && !CommonUtil.isEmpty(user.getPwd())){
            return buildFailureJson(StatusConstant.OBJECT_EXIST,"手机号已经存在");
        }
        User r = new User();
        if(null == user){
            r.setPhone(phone);
            r.setPwd(pwd);
            r.setDeviceToken(deviceToken);
            r.setDeviceType(deviceType);
            r.setRoleId(RoleConstant.REGULAR_MEMBERS);
            r.setShareCode(ShareCodeUtil.serialCode(Long.parseLong(phone)));
            userService.save(r);
        }
        else{
            r = user;
            r.setPwd(pwd);
            r.setDeviceToken(deviceToken);
            r.setDeviceType(deviceType);
            r.setRoleId(RoleConstant.REGULAR_MEMBERS);
            userService.update(r);
        }
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        r.setToken(token);
        redisService.set(token,r,StatusConstant.LOGIN_VALID,TimeUnit.DAYS);
        userService.update(r);
        return buildSuccessJson(StatusConstant.SUCCESS_CODE,"注册成功",r);
    }



    @RequestMapping(value = "/setPayPwd",method = RequestMethod.POST)
    @ApiOperation(value = "设置支付密码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "payPwd",value = "加过密的支付密码",required = true)
    })
    public ResponseData setPayPwd(String payPwd){
        if(CommonUtil.isEmpty(payPwd)){
            return buildFailureJson(StatusConstant.FIELD_NOT_NULL,"参数不能为空");
        }
        try {
            User user = LoginHelper.getCurrentUser(redisService);
            User u = new User();
            u.setId(user.getId());
            u.setPayPwd(payPwd);
            userService.update(u);
        } catch (InterfaceCommonException e) {
            return buildFailureJson(e.getErrorCode(),e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return buildFailureJson(StatusConstant.Fail_CODE,"设置失败");
        }
        return buildSuccessCodeJson(StatusConstant.SUCCESS_CODE,"设置成功");
    }



    @RequestMapping(value = "/login",method = RequestMethod.POST)
    @ApiOperation(value = "常规登录(非微信、QQ)")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "phone",value = "手机号",required = true),
            @ApiImplicitParam(name = "pwd",value = "密码",required = true),
            @ApiImplicitParam(name = "deviceToken",value = "设备token"),
            @ApiImplicitParam(name = "deviceType",value = "设备类型 0 android  1 ios")
    })
    public ResponseData login(String phone,String pwd,String deviceToken,Integer deviceType){

        if(CommonUtil.isEmpty(phone,pwd)){
            return buildFailureJson(StatusConstant.FIELD_NOT_NULL,"参数不能为空");
        }
        User user = userService.getUserByPhone(phone);
        if(null == user || StatusConstant.INVALID.equals(user.getStatus())){
            return buildFailureJson(StatusConstant.OBJECT_NOT_EXIST,"手机号不存在");
        }
        if(!pwd.equals(user.getPwd())){
            return buildFailureJson(StatusConstant.Fail_CODE,"密码错误");
        }
        user.setDeviceToken(deviceToken);
        user.setDeviceType(deviceType);
        user.setPayPwd(null);
        if(!CommonUtil.isEmpty(user.getToken())){
            redisService.remove(user.getToken());
        }
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        user.setToken(token);
        redisService.set(token,user,StatusConstant.LOGIN_VALID,TimeUnit.DAYS);
        userService.update(user);
        return buildSuccessJson(StatusConstant.SUCCESS_CODE,"登录成功",user);
    }


    @RequestMapping(value = "/getInfo",method = RequestMethod.POST)
    @ApiOperation(value = "获取个人基本信息")
    public ResponseData getInfo(){
        try {
            User user = LoginHelper.getCurrentUser(redisService);
            return buildSuccessJson(StatusConstant.SUCCESS_CODE,"获取成功",user);
        } catch (InterfaceCommonException e) {
            return buildFailureJson(e.getErrorCode(),e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return buildFailureJson(StatusConstant.Fail_CODE,"设置失败");
        }
    }


    @RequestMapping(value = "/update",method = RequestMethod.POST)
    @ApiOperation(value = "更新用户字段操作")
    public ResponseData setBaseInfo(User user){
        try {
            User currentUser = LoginHelper.getCurrentUser(redisService);
            user.setPayPwd(null);
            user.setId(currentUser.getId());
            userService.update(user);

            User sql = userService.find("id", user.getId());
            redisService.set(currentUser.getToken(),sql,StatusConstant.LOGIN_VALID,TimeUnit.DAYS);
            return buildSuccessCodeJson(StatusConstant.SUCCESS_CODE,"操作成功");
        } catch (InterfaceCommonException e) {
            return buildFailureJson(e.getErrorCode(),e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return buildFailureJson(StatusConstant.Fail_CODE,"设置失败");
        }
    }


    @RequestMapping(value = "/getDistributionUser",method = RequestMethod.POST)
    @ApiOperation(value = "获取当前分销用户",notes = "返回格式：{'one':[],'two':[],'three':[]}")
    public ResponseData getDistributionUser(){
        try {
            User user = LoginHelper.getCurrentUser(redisService);
            return buildSuccessJson(StatusConstant.SUCCESS_CODE,"获取成功",
                    userService.getDistributionUser(user.getId(),null,null));
        } catch (InterfaceCommonException e) {
            return buildFailureJson(e.getErrorCode(),e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return buildFailureJson(StatusConstant.Fail_CODE,"设置失败");
        }
    }



    @RequestMapping(value = "/getIncomeDetail",method = RequestMethod.POST)
    @ApiOperation(value = "获取收益明细集合")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "fromUserId",value = "明细来源用户ID",required = true),
            @ApiImplicitParam(name = "pageNO",value = "分页参数 从 1 开始",required = true),
            @ApiImplicitParam(name = "pageSize",value = "分页参数",required = true)
    })
    public ResponseData getIncomeDetail(String fromUserId,Integer pageNO,Integer pageSize){

        if(CommonUtil.isEmpty(fromUserId,pageNO,pageSize)){
            return buildFailureJson(StatusConstant.FIELD_NOT_NULL,"参数不能为空");
        }
        try {
            User user = LoginHelper.getCurrentUser(redisService);
            return buildSuccessJson(StatusConstant.SUCCESS_CODE,"获取成功",
                    incomeDetailService.getIncomeDetail(fromUserId,user.getId(),pageNO,pageSize));
        } catch (InterfaceCommonException e) {
            return buildFailureJson(e.getErrorCode(),e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return buildFailureJson(StatusConstant.Fail_CODE,"设置失败");
        }
    }



    @RequestMapping(value = "/shareCodeRegister",method = RequestMethod.POST)
    @ApiOperation(value = "分享页注册提交")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "shareCode",value = "邀请码",required = true),
            @ApiImplicitParam(name = "phone",value = "手机号",required = true)
    })
    public ResponseData shareCodeRegister(String shareCode,String phone){

        if(CommonUtil.isEmpty(shareCode,phone)){
            return buildFailureJson(StatusConstant.FIELD_NOT_NULL,"参数错误");
        }
        User user = userService.getUserByShareCode(shareCode);
        if(null == user){
            return buildFailureJson(StatusConstant.Fail_CODE,"邀请码错误");
        }
        if (null != user.getPhone() && user.getPhone().equals(phone)) {
            return buildFailureJson(StatusConstant.Fail_CODE,"自己不能成为自己的分销商");
        }
        User userPhone = userService.getUserByPhone(phone);
        if(null != userPhone && !CommonUtil.isEmpty(userPhone.getParentId())){
            return buildFailureJson(StatusConstant.Fail_CODE,"已经提交过");
        }
        if(null == userPhone){
            User u = new User();
            u.setPhone(phone);
            u.setRoleId(RoleConstant.REGULAR_MEMBERS);
            u.setParentId(user.getId());
            u.setShareCode(ShareCodeUtil.serialCode(Long.parseLong(phone)));
            userService.save(u);
        }
        if(null != userPhone && null == userPhone.getParentId()){
            userPhone.setParentId(user.getId());
            userService.update(userPhone);
        }
        return buildSuccessCodeJson(StatusConstant.SUCCESS_CODE,"提交成功");
    }



    @RequestMapping(value = "/countLastDay",method = RequestMethod.POST)
    @ApiOperation(value = "统计昨日数据集合 积分(score)、欢喜券(bigDecimal)、单元总量(暂无)、转化率(parities)")
    public ResponseData countLastDay(){
        return buildSuccessJson(StatusConstant.SUCCESS_CODE,"提交成功",
                userService.countLastDay());
    }



    @RequestMapping(value = "/logout",method = RequestMethod.POST)
    @ApiOperation(value = "牛B的安全退出")
    public ResponseData logout(){
        try {
            User user = LoginHelper.getCurrentUser(redisService);
            redisService.remove(user.getToken());
            return buildSuccessCodeJson(StatusConstant.SUCCESS_CODE,"退出成功");
        } catch (InterfaceCommonException e) {
            return buildFailureJson(e.getErrorCode(),e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return buildSuccessCodeJson(StatusConstant.SUCCESS_CODE,"退出成功");
        }
    }






}
