package net.mokich.panopticon.perms;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

public final class PermsApiHook {
    private static final Method CHECK = resolve();

    private PermsApiHook() {
    }

    private static Method resolve() {
        try {
            Class<?> cls = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            return cls.getMethod("check", Entity.class, String.class);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean check(Entity entity, String node) {
        if (CHECK == null) {
            return false;
        }
        try {
            return (Boolean) CHECK.invoke(null, entity, node);
        } catch (Throwable t) {
            return false;
        }
    }
}