package com.familyledger.state;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.familyledger.entity.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class StateSnapshotMappingTest {

    @Test
    void refreshCanClearPriorCollectionErrorFields() throws Exception {
        assertThat(updateStrategy("errorCode")).isEqualTo(FieldStrategy.ALWAYS);
        assertThat(updateStrategy("errorMessage")).isEqualTo(FieldStrategy.ALWAYS);
    }

    private FieldStrategy updateStrategy(String fieldName) throws Exception {
        Field field = StateSnapshot.class.getDeclaredField(fieldName);
        return field.getAnnotation(TableField.class).updateStrategy();
    }
}
