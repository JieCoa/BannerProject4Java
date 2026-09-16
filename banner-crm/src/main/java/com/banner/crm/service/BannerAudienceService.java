package com.banner.crm.service;

import com.banner.common.entity.BannerUserShard;
import com.banner.crm.mapper.BannerUserShardMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** CRM 侧定向用户名单的分片存储与查询服务。 */
@Service
@RequiredArgsConstructor
public class BannerAudienceService {

    public static final int SHARD_SIZE = 1000;
    private final BannerUserShardMapper shardMapper;

    public void replace(Long bannerId, Set<Long> userIds, long version) {
        shardMapper.delete(new LambdaQueryWrapper<BannerUserShard>()
                .eq(BannerUserShard::getBannerId, bannerId));
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        for (int from = 0, shardNo = 0; from < ids.size(); from += SHARD_SIZE, shardNo++) {
            List<Long> shard = ids.subList(from, Math.min(from + SHARD_SIZE, ids.size()));
            BannerUserShard entity = new BannerUserShard();
            entity.setBannerId(bannerId);
            entity.setShardNo(shardNo);
            entity.setUserIds(shard.stream().map(String::valueOf).collect(Collectors.joining(",")));
            entity.setVersion(version);
            shardMapper.insert(entity);
        }
    }

    public Set<Long> findUserIds(Long bannerId) {
        List<BannerUserShard> shards = shardMapper.selectList(new LambdaQueryWrapper<BannerUserShard>()
                .eq(BannerUserShard::getBannerId, bannerId)
                .orderByAsc(BannerUserShard::getShardNo));
        Set<Long> result = new LinkedHashSet<>();
        for (BannerUserShard shard : shards) {
            if (shard.getUserIds() == null || shard.getUserIds().isBlank()) {
                continue;
            }
            Arrays.stream(shard.getUserIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).forEach(result::add);
        }
        return result;
    }
}
