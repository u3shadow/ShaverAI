package com.u3coding.shaver.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "wifi_aliases",
    indices = {
        @Index(value = {"alias"}, unique = true),
        @Index(value = {"ssid"}, unique = true)
    }
)
public class WifiAliasEntity {
    @PrimaryKey
    @NonNull
    public String ssid;

    @NonNull
    public String alias;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public WifiAliasEntity(@NonNull String ssid, @NonNull String alias, long createdAt, long updatedAt) {
        this.ssid = ssid;
        this.alias = alias;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
