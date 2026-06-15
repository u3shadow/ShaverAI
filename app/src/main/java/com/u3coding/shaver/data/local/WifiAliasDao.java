package com.u3coding.shaver.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WifiAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(WifiAliasEntity entity);

    @Query("SELECT * FROM wifi_aliases ORDER BY alias")
    List<WifiAliasEntity> getAll();

    @Query("SELECT * FROM wifi_aliases WHERE ssid = :ssid LIMIT 1")
    WifiAliasEntity findBySsid(String ssid);

    @Query("SELECT ssid FROM wifi_aliases WHERE alias = :alias LIMIT 1")
    String findSsidByAlias(String alias);

    @Query("DELETE FROM wifi_aliases WHERE ssid = :ssid")
    void deleteBySsid(String ssid);
}
