package com.actionworks.flashsale.app.service.impl;

import com.actionworks.flashsale.app.auth.AuthorizationService;
import com.actionworks.flashsale.app.auth.model.AuthResult;
import com.actionworks.flashsale.app.cache.FlashItemCacheService;
import com.actionworks.flashsale.app.cache.FlashItemsCacheService;
import com.actionworks.flashsale.app.cache.ItemStockCacheService;
import com.actionworks.flashsale.app.cache.model.FlashItemCache;
import com.actionworks.flashsale.app.cache.model.FlashItemsCache;
import com.actionworks.flashsale.app.cache.model.ItemStockCache;
import com.actionworks.flashsale.app.model.builder.FlashItemAppBuilder;
import com.actionworks.flashsale.app.model.command.FlashItemPublishCommand;
import com.actionworks.flashsale.app.model.dto.FlashItemDTO;
import com.actionworks.flashsale.app.model.query.FlashItemsQuery;
import com.actionworks.flashsale.app.model.result.AppMultiResult;
import com.actionworks.flashsale.app.model.result.AppResult;
import com.actionworks.flashsale.app.model.result.AppSingleResult;
import com.actionworks.flashsale.app.service.FlashItemAppService;
import com.actionworks.flashsale.controller.exception.AuthException;
import com.actionworks.flashsale.domain.model.PageResult;
import com.actionworks.flashsale.domain.model.entity.FlashActivity;
import com.actionworks.flashsale.domain.model.entity.FlashItem;
import com.actionworks.flashsale.domain.service.FlashActivityDomainService;
import com.actionworks.flashsale.domain.service.FlashItemDomainService;
import com.alibaba.cola.exception.BizException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.actionworks.flashsale.app.auth.model.ResourceEnum.FLASH_ITEMS_GET;
import static com.actionworks.flashsale.app.auth.model.ResourceEnum.FLASH_ITEM_CREATE;
import static com.actionworks.flashsale.app.auth.model.ResourceEnum.FLASH_ITEM_OFFLINE;
import static com.actionworks.flashsale.app.exception.AppErrorCode.ACTIVITY_NOT_FOUND;
import static com.actionworks.flashsale.app.model.builder.FlashItemAppBuilder.toDomain;
import static com.actionworks.flashsale.app.model.builder.FlashItemAppBuilder.toFlashItemsQuery;
import static com.actionworks.flashsale.controller.exception.ErrorCode.INVALID_TOKEN;

@Service
public class FlashItemAppServiceImpl implements FlashItemAppService {

    @Resource
    private FlashItemDomainService flashItemDomainService;

    @Resource
    private FlashActivityDomainService flashActivityDomainService;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private FlashItemCacheService flashItemCacheService;
    @Resource
    private FlashItemsCacheService flashItemsCacheService;
    @Resource
    private ItemStockCacheService itemStockCacheService;

    @Override
    public AppResult publishFlashItem(String token, Long activityId, FlashItemPublishCommand flashItemPublishCommand) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEM_CREATE);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }
        FlashActivity flashActivity = flashActivityDomainService.getFlashActivity(activityId);
        if (flashActivity == null) {
            throw new BizException(ACTIVITY_NOT_FOUND.getErrDesc());
        }
        FlashItem flashItem = toDomain(flashItemPublishCommand);
        flashItem.setActivityId(activityId);
        flashItem.setStockWarmUp(0);
        flashItemDomainService.publishFlashItem(flashItem);
        return AppResult.buildSuccess();
    }

    @Override
    public AppResult onlineFlashItem(String token, Long activityId, Long itemId) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEM_OFFLINE);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }
        flashItemDomainService.onlineFlashItem(itemId);
        return AppResult.buildSuccess();
    }

    @Override
    public AppResult offlineFlashItem(String token, Long activityId, Long itemId) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEM_OFFLINE);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }
        flashItemDomainService.offlineFlashItem(itemId);
        return AppResult.buildSuccess();
    }

    @Override
    public AppMultiResult<FlashItemDTO> getFlashItems(String token, Long activityId, FlashItemsQuery flashItemsQuery) {
        if (flashItemsQuery == null) {
            return AppMultiResult.empty();
        }
        flashItemsQuery.setActivityId(activityId);
        List<FlashItem> activities;
        Integer total;
        if (flashItemsQuery.isOnlineFirstPageQuery()) {
            FlashItemsCache flashItemsCache = flashItemsCacheService.getCachedItems(activityId, flashItemsQuery.getVersion());
            if (flashItemsCache.isLater()) {
                return AppMultiResult.tryLater();
            }
            activities = flashItemsCache.getFlashItems();
            total = flashItemsCache.getTotal();
        } else {
            PageResult<FlashItem> flashItemPageResult = flashItemDomainService.getFlashItems(toFlashItemsQuery(flashItemsQuery));
            activities = flashItemPageResult.getData();
            total = flashItemPageResult.getTotal();
        }

        List<FlashItemDTO> flashItemDTOList = activities.stream().map(FlashItemAppBuilder::toFlashItemDTO).collect(Collectors.toList());
        return AppMultiResult.of(flashItemDTOList, total);
    }


    @Override
    public AppSingleResult<FlashItemDTO> getFlashItem(String token, Long activityId, Long itemId, Long version) {
        AuthResult authResult = authorizationService.auth(token, FLASH_ITEMS_GET);
        if (!authResult.isSuccess()) {
            throw new AuthException(INVALID_TOKEN);
        }

        FlashItemCache flashItemCache = flashItemCacheService.getCachedItem(itemId, version);
        if (!flashItemCache.isExist()) {
            throw new BizException(ACTIVITY_NOT_FOUND.getErrDesc());
        }
        if (flashItemCache.isLater()) {
            return AppSingleResult.tryLater();
        }
        updateLatestItemStock(flashItemCache.getFlashItem());
        FlashItemDTO flashItemDTO = FlashItemAppBuilder.toFlashItemDTO(flashItemCache.getFlashItem());
        flashItemDTO.setVersion(flashItemCache.getVersion());
        return AppSingleResult.of(flashItemDTO);
    }

    private void updateLatestItemStock(FlashItem flashItem) {
        if (flashItem == null) {
            return;
        }
        ItemStockCache itemStockCache = itemStockCacheService.getAvailableItemStock(flashItem.getId());
        if (itemStockCache.isSuccess() && itemStockCache.getAvailableStock() != null) {
            flashItem.setAvailableStock(itemStockCache.getAvailableStock());
        }
    }
}
