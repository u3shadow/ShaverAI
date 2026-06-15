package com.u3coding.shaver.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RuleEntity entity);

    @Query("SELECT * FROM rules WHERE trigger = :trigger AND event_type = :eventType ORDER BY operation")
    List<RuleEntity> getByTriggerAndEvent(String trigger, String eventType);
}
