package com.u3coding.shaver.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "rules",
    indices = {@Index(value = {"trigger", "operation"}, unique = true)}
)
public class RuleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String trigger;

    @NonNull
    public String operation;

    @Nullable
    public String value;

    public RuleEntity(@NonNull String trigger, @NonNull String operation, @Nullable String value) {
        this.trigger = trigger;
        this.operation = operation;
        this.value = value;
    }
}
