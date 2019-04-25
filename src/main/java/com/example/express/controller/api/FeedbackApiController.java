package com.example.express.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.express.common.util.StringUtils;
import com.example.express.domain.ResponseResult;
import com.example.express.domain.bean.SysUser;
import com.example.express.domain.bean.UserFeedback;
import com.example.express.domain.enums.FeedbackStatusEnum;
import com.example.express.domain.enums.FeedbackTypeEnum;
import com.example.express.domain.enums.ResponseErrorCodeEnum;
import com.example.express.domain.vo.BootstrapTableVO;
import com.example.express.domain.vo.UserFeedbackDescVO;
import com.example.express.domain.vo.UserFeedbackVO;
import com.example.express.service.OrderInfoService;
import com.example.express.service.UserFeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * API 反馈接口
 * @author xiangsheng.wu
 * @date 2019年04月23日 15:14
 */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackApiController extends BaseApiController {
    @Autowired
    private UserFeedbackService userFeedbackService;
    @Autowired
    private OrderInfoService orderInfoService;

    /**
     * 获取单条记录详情
     */
    @GetMapping("/{id}")
    public ResponseResult getFeedbackById(@PathVariable Integer id) {
        UserFeedbackDescVO feedback = userFeedbackService.getDescVO(id);

        return ResponseResult.success(feedback);
    }

    /**
     * 分页查询当前用户所有反馈记录
     * - 管理员：查询所有人所有反馈
     * - 配送员：查询所有人所有订单反馈
     * - 普通用户：查询个人所有反馈
     */
    @GetMapping("/list")
    public BootstrapTableVO<UserFeedbackVO> listFeedback(@RequestParam(required = false, defaultValue = "1") Integer current,
                                                         @RequestParam(required = false, defaultValue = "10") Integer size,
                                                         String type, String status, String id,
                                                         @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                                                         @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
                                                         @AuthenticationPrincipal SysUser sysUser) {
        Page<UserFeedback> page = new Page<>(current, size);
        QueryWrapper<UserFeedback> wrapper = new QueryWrapper<>();

        Integer feedStatus = StringUtils.toInteger(status, -1);
        if(FeedbackStatusEnum.getByStatus(feedStatus) != null) {
            wrapper.eq("status", feedStatus);
        }

        if(StringUtils.isNotBlank(id)) {
            wrapper.eq("id", id);
        }
        if(startDate != null) {
            wrapper.ge("create_date", startDate);
        }
        if(endDate != null) {
            wrapper.le("create_date", startDate);
        }

        switch (sysUser.getRole()) {
            case ADMIN:
                Integer feedbackType = StringUtils.toInteger(type, -1);
                if(FeedbackTypeEnum.getByType(feedbackType) != null) {
                    wrapper.eq("type", feedbackType);
                }

                return userFeedbackService.pageUserFeedbackVO(page, wrapper);
            case COURIER:
                wrapper.eq("type", FeedbackTypeEnum.ORDER.getType());

                return userFeedbackService.pageUserFeedbackVO(page,wrapper);
            case USER:
                wrapper.eq("user_id", sysUser.getId());

                Integer feedbackType1 = StringUtils.toInteger(type, -1);
                if(FeedbackTypeEnum.getByType(feedbackType1) != null) {
                    wrapper.eq("type", feedbackType1);
                }

                return userFeedbackService.pageUserFeedbackVO(page, wrapper);
            default:
                return new BootstrapTableVO();
        }
    }

    /**
     * 创建反馈记录
     * - 仅支持 配送员 & 普通用户
     * @author jitwxs
     * @date 2019/4/23 23:13
     */
    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_COURIER')")
    public ResponseResult createFeedback(String type, String orderId, String content, @AuthenticationPrincipal SysUser sysUser) {
        // 校验参数
        Integer feedbackType = StringUtils.toInteger(type, -1);
        FeedbackTypeEnum feedbackTypeEnum = FeedbackTypeEnum.getByType(feedbackType);
        if(feedbackTypeEnum == null) {
            return ResponseResult.failure(ResponseErrorCodeEnum.FEEDBACK_TYPE_ERROR);
        }

        if(StringUtils.isBlank(content)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.FEEDBACK_NOT_EMPTY);
        }
        if(content.length() > CONTENT_MAX_LENGTH) {
            return ResponseResult.failure(ResponseErrorCodeEnum.FEEDBACk_LENGTH_OVER_255);
        }
        if(StringUtils.isNotBlank(orderId)) {
            if(!orderInfoService.isExist(orderId)) {
                return ResponseResult.failure(ResponseErrorCodeEnum.ORDER_NOT_EXIST);
            }
        }

        boolean isSuccess = userFeedbackService.createFeedback(sysUser.getId(), feedbackTypeEnum, content, orderId);

        return isSuccess ? ResponseResult.success() : ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
    }

    /**
     * 审批反馈记录
     */
    @PostMapping("/deal")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_COURIER')")
    public ResponseResult dealFeedback(String id, Integer status, String result, @AuthenticationPrincipal SysUser sysUser) {
        // 校验参数
        UserFeedback feedback = userFeedbackService.getById(id);
        FeedbackStatusEnum statusEnum = FeedbackStatusEnum.getByStatus(status);
        if(feedback == null || statusEnum == null || StringUtils.isBlank(result)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PARAMETER_ERROR);
        }
        if(result.length() > CONTENT_MAX_LENGTH) {
            return ResponseResult.failure(ResponseErrorCodeEnum.FEEDBACk_LENGTH_OVER_255);
        }

        feedback.setHandler(sysUser.getId());
        feedback.setFeedbackStatus(statusEnum);
        feedback.setResult(result);

        boolean isSuccess = userFeedbackService.updateById(feedback);
        return isSuccess ? ResponseResult.success() : ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
    }
}
