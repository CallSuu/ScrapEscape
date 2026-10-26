package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.*;
import java.util.ArrayList;

/** Preserve old quantities, condition and age before enforcing the six-item cap. */
public class V3__Limit_batch_size extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        record Batch(long id,String game,long material,int quantity,String storage,int hour,String condition) {}
        var batches=new ArrayList<Batch>();
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT * FROM item_batch WHERE quantity>6")) {
            while(rows.next()) batches.add(new Batch(rows.getLong("batch_id"),rows.getString("game_id"),rows.getLong("material_id"),
                rows.getInt("quantity"),rows.getString("storage_type"),rows.getInt("acquired_hour"),rows.getString("item_condition")));
        }
        try(var update=connection.prepareStatement("UPDATE item_batch SET quantity=6 WHERE batch_id=?");
            var insert=connection.prepareStatement("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,?,?,?,?,?)")) {
            for(var b:batches) {
                update.setLong(1,b.id());update.executeUpdate();
                for(int remaining=b.quantity()-6;remaining>0;remaining-=6) {
                    insert.setString(1,b.game());insert.setLong(2,b.material());insert.setInt(3,Math.min(6,remaining));
                    insert.setString(4,b.storage());insert.setInt(5,b.hour());insert.setString(6,b.condition());insert.executeUpdate();
                }
            }
        }
    }
}
