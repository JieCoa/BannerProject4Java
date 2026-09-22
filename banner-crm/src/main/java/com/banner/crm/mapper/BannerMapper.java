package com.banner.crm.mapper;

import com.banner.common.entity.BannerInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** banner_info 的持久化操作。 */
public interface BannerMapper extends BaseMapper<BannerInfo> {

    /** 使用期望版本更新，并由数据库原子递增新版本。 */
    @Update("UPDATE banner_info SET "
            + "biz_id = COALESCE(#{banner.bizId}, biz_id), "
            + "title = COALESCE(#{banner.title}, title), "
            + "image_url = COALESCE(#{banner.imageUrl}, image_url), "
            + "jump_url = COALESCE(#{banner.jumpUrl}, jump_url), "
            + "start_time = COALESCE(#{banner.startTime}, start_time), "
            + "end_time = COALESCE(#{banner.endTime}, end_time), "
            + "sort = COALESCE(#{banner.sort}, sort), "
            + "version = version + 1, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{banner.id} AND version = #{expectedVersion}")
    int updateByIdAndVersion(@Param("banner") BannerInfo banner,
                             @Param("expectedVersion") Long expectedVersion);

    /** 删除前原子递增版本，返回删除事件应携带的新版本。 */
    @Update("UPDATE banner_info SET version = version + 1, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND version = #{expectedVersion}")
    int incrementVersionBeforeDelete(@Param("id") Long id,
                                     @Param("expectedVersion") Long expectedVersion);
}
