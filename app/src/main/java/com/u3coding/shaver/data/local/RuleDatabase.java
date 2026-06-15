package com.u3coding.shaver.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {RuleEntity.class, WifiAliasEntity.class}, version = 3, exportSchema = false)
public abstract class RuleDatabase extends RoomDatabase {
    public abstract RuleDao ruleDao();
    public abstract WifiAliasDao wifiAliasDao();
}
