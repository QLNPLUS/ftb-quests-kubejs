package dev.questjs.ftbquestskubejs.data;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomDataNumberPathReadTest {
    @Test
    void allNumericTagTypesReadAsDouble() {
        CompoundTag root = new CompoundTag();
        root.put("b", ByteTag.ZERO);
        root.put("s", ShortTag.valueOf((short) -3));
        root.put("i", IntTag.valueOf(-7));
        root.put("l", LongTag.valueOf(-11L));
        root.put("f", FloatTag.valueOf(-1.25f));
        root.put("d", DoubleTag.valueOf(-2.5));

        assertEquals(0.0, read(root, "b").orElseThrow());
        assertEquals(-3.0, read(root, "s").orElseThrow());
        assertEquals(-7.0, read(root, "i").orElseThrow());
        assertEquals(-11.0, read(root, "l").orElseThrow());
        assertEquals(-1.25, read(root, "f").orElseThrow());
        assertEquals(-2.5, read(root, "d").orElseThrow());
    }

    @Test
    void nestedPathsResolve() {
        CompoundTag shop = new CompoundTag();
        shop.putDouble("balance", 120.5);
        CompoundTag root = new CompoundTag();
        root.put("shop", shop);

        assertEquals(120.5, read(root, "shop.balance").orElseThrow());
    }

    @Test
    void missingOrNonNumericYieldEmpty() {
        CompoundTag inner = new CompoundTag();
        inner.put("text", StringTag.valueOf("abc"));
        inner.put("list", new ListTag());

        CompoundTag root = new CompoundTag();
        root.put("inner", inner);

        assertTrue(read(root, "nope").isEmpty());
        assertTrue(read(root, "inner.text").isEmpty());
        assertTrue(read(root, "inner.list").isEmpty());
        assertTrue(read(root, "inner.missing.deep").isEmpty());
        assertTrue(read(root, "inner.text.deeper").isEmpty());
    }

    @Test
    void rootsResolveInOrderWithNullsSkipped() {
        CompoundTag kubeJs = new CompoundTag();
        kubeJs.putInt("test", 11);
        CompoundTag neoforge = new CompoundTag();
        neoforge.putDouble("other", -2.5);

        assertEquals(11.0, CustomDataNumberTask.readNumericRoots(kubeJs, neoforge, "test").orElseThrow());
        assertEquals(-2.5, CustomDataNumberTask.readNumericRoots(kubeJs, neoforge, "other").orElseThrow());
        assertEquals(11.0, CustomDataNumberTask.readNumericRoots(null, kubeJs, "test").orElseThrow());
        assertTrue(CustomDataNumberTask.readNumericRoots(null, null, "test").isEmpty());
    }

    private static OptionalDouble read(CompoundTag root, String path) {
        return CustomDataNumberTask.readNumericPath(root, path);
    }
}
