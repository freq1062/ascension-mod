import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;

public class TestT {
    void a(ServerLevel sl) {
        Entity e = EntityType.PIG.create(sl, EntitySpawnReason.COMMAND);
        e.teleportTo(0, 0, 0);
    }
}
