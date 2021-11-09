package com.actionworks.flashsale.app.service.impl;

import com.actionworks.flashsale.app.auth.AuthorizationService;
import com.actionworks.flashsale.app.auth.model.AuthResult;
import com.actionworks.flashsale.app.cache.FlashActivitiesCacheService;
import com.actionworks.flashsale.app.cache.FlashActivityCacheService;
import com.actionworks.flashsale.app.cache.model.FlashActivitiesCache;
import com.actionworks.flashsale.app.cache.model.FlashActivityCache;
import com.actionworks.flashsale.app.model.builder.FlashActivityAppBuilder;
import com.actionworks.flashsale.app.model.command.FlashActivityPublishCommand;
import com.actionworks.flashsale.app.model.dto.FlashActivityDTO;
import com.actionworks.flashsale.app.model.query.FlashActivitiesQuery;
import com.actionworks.flashsale.app.model.result.AppMultiResult;
import com.actionworks.flashsale.app.model.result.AppResult;
import com.actionworks.flashsale.app.model.result.AppSingleResult;
import com.actionworks.flashsale.app.service.FlashActivityAppService;
import com.actionworks.flashsale.controller.exception.AuthException;
import com.actionworks.flashsale.domain.model.PageResult;
import com.actionworks.flashsale.domain.model.entity.FlashActivity;
import com.actionworks.flashsale.domain.service.FlashActivityDomainService;
import com.alibaba.cola.exception.BizException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.actionworks.flashsale.app.auth.model.ResourceEnum.FLASH_ITEMS_GET;
import static com.actionworks.flashsale.app.auth.model.ResourceEnum.FLASH_ITEM_CREATE;
import static com.actionworks.flashsale.app.exception.AppErrorCode.ACTIVITY_NOT_FOUND;
import static com.actionworks.flashsale.app.model.builder.FlashActivityAppBuilder.toDomain;
import static com.actionworks.flashsale.app.model.builder.FlashActivityAppBuilder.toFlashActivitiesQuery;
import static com.actionworks.flashsale.controller.exception.ErrorCode.INVALID_TOKEN;

@Service
public class DefaultActivityAppService implements FlashActivityAppService {

    @Resource
    private FlashActivityDomainService flashActivityDomainService;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private FlashActivityCacheService flashActivityCacheService;
    @Resource
    private FlashActivitiesCacheService flashActivitiesCacheService;

    @Override
    public AppResult publishFlashActivity(String token, FlashActivityPublishCommand flashActivityPublishCommand) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEM_CREATE);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }
        flashActivityDomainService.publishActivity(authResult.getUserId(), toDomain(flashActivityPublishCommand));
        return AppResult.buildSuccess();
    }

    @Override
    public AppResult modifyFlashActivity(String token, Long activityId, FlashActivityPublishCommand flashActivityPublishCommand) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEM_CREATE);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }
        FlashActivity flashActivity = toDomain(flashActivityPublishCommand);
        flashActivity.setId(activityId);
        flashActivityDomainService.modifyActivity(authResult.getUserId(), flashActivity);
        return AppResult.buildSuccess();
    }

    @Override
    public AppResult onlineFlashActivity(String token, Long activityId) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEM_CREATE);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }
        flashActivityDomainService.onlineActivity(authResult.getUserId(), activityId);
        return AppResult.buildSuccess();
    }

    @Override
    public AppResult offlineFlashActivity(String token, Long activityId) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEM_CREATE);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }
        flashActivityDomainService.offlineActivity(authResult.getUserId(), activityId);
        return AppResult.buildSuccess();
    }

    @Override
    public AppMultiResult<FlashActivityDTO> getFlashActivities(String token, FlashActivitiesQuery flashActivitiesQuery) {
        List<FlashActivity> activities;
        Integer total;
        if (flashActivitiesQuery.isFirstPureQuery()) {
            FlashActivitiesCache flashActivitiesCache = flashActivitiesCacheService.getCachedActivities(flashActivitiesQuery.getPageNumber(), flashActivitiesQuery.getVersion());
            if (flashActivitiesCache.isLater()) {
                return AppMultiResult.tryLater();
            }
            activities = flashActivitiesCache.getFlashActivities();
            total = flashActivitiesCache.getTotal();
        } else {
            PageResult<FlashActivity> flashActivityPageResult = flashActivityDomainService.getFlashActivities(toFlashActivitiesQuery(flashActivitiesQuery));
            activities = flashActivityPageResult.getData();
            total = flashActivityPageResult.getTotal();
        }

        List<FlashActivityDTO> flashActivityDTOList = activities.stream().map(FlashActivityAppBuilder::toFlashActivityDTO).collect(Collectors.toList());
        return AppMultiResult.of(flashActivityDTOList, total);
    }

    @Override
    public AppSingleResult<FlashActivityDTO> getFlashActivity(String token, Long activityId, Long version) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEMS_GET);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }

        FlashActivityCache flashActivityCache = flashActivityCacheService.getCachedActivity(activityId, version);
        if (!flashActivityCache.isExist()) {
            throw new BizException(ACTIVITY_NOT_FOUND.getErrDesc());
        }
        if (flashActivityCache.isLater()) {
            return AppSingleResult.tryLater();
        }
        FlashActivityDTO flashActivityDTO = FlashActivityAppBuilder.toFlashActivityDTO(flashActivityCache.getFlashActivity());
        flashActivityDTO.setVersion(flashActivityCache.getVersion());
        return AppSingleResult.ok(flashActivityDTO);
    }

    @Override
    public AppSingleResult<FlashActivityDTO> getFlashActivity(Long activityId) {
        FlashActivityCache flashActivityCache = flashActivityCacheService.getCachedActivity(activityId, null);
        if (!flashActivityCache.isExist()) {
            throw new BizException(ACTIVITY_NOT_FOUND.getErrDesc());
        }
        if (flashActivityCache.isLater()) {
            return AppSingleResult.tryLater();
        }
        FlashActivityDTO flashActivityDTO = FlashActivityAppBuilder.toFlashActivityDTO(flashActivityCache.getFlashActivity());
        flashActivityDTO.setVersion(flashActivityCache.getVersion());
        return AppSingleResult.ok(flashActivityDTO);
    }
}