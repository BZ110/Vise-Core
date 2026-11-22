package org.albi.core.serializable;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

/**
 * A self-contained, fully serialisable wrapper around an ItemStack.
 * <p>All metadata (display-name, lore, enchants, attributes, custom model
 * data, NBT, etc.) is preserved because we serialise with BukkitObject streams
 * instead of hand-editing maps.</p>
 */
public class SerializableItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /* ------------------------------------------------------------------ */
    /*  Stored data                                                       */
    /* ------------------------------------------------------------------ */
    private String base64;       // the encoded ItemStack
    private Object extraValue;   // untouched (for your own extensions)

    /* ------------------------------------------------------------------ */
    /*  Constructors                                                      */
    /* ------------------------------------------------------------------ */
    public SerializableItem(ItemStack item) {
        convertFromItem(item);
    }
    public SerializableItem(ItemStack item, Object extra) {
        convertFromItem(item);
        this.extraValue = extra;
    }
    public SerializableItem() { }             // allow no-arg

    /* ------------------------------------------------------------------ */
    /*  Serialise                                                         */
    /* ------------------------------------------------------------------ */
    public final void convertFromItem(ItemStack item) {
        if (item == null) {
            this.base64 = null;
            return;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {

            oos.writeObject(item);
            oos.flush();
            this.base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (IOException ex) {
            throw new IllegalStateException("Could not serialise ItemStack", ex);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Deserialise                                                       */
    /* ------------------------------------------------------------------ */
    public ItemStack convertToItem() {
        if (base64 == null) return null;

        byte[] data = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             BukkitObjectInputStream  ois = new BukkitObjectInputStream(bais)) {

            return (ItemStack) ois.readObject();

        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Could not deserialise ItemStack", ex);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Convenience Getters                                               */
    /* ------------------------------------------------------------------ */
    public String getName() {
        ItemStack item = convertToItem();
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
        }
        return "";
    }
    public String getType()        { ItemStack it = convertToItem(); return it != null ? it.getType().name() : ""; }
    public int    getAmount()      { ItemStack it = convertToItem(); return it != null ? it.getAmount()      : 0; }
    public short  getDurability()  { ItemStack it = convertToItem(); return it != null ? it.getDurability()  : 0; }

    /* ------------------------------------------------------------------ */
    /*  Extra-value helpers                                               */
    /* ------------------------------------------------------------------ */
    public Object getExtraValue()               { return extraValue; }
    public void   setExtraValue(Object extra)   { this.extraValue = extra; }

    /* ------------------------------------------------------------------ */
    /*  Debug                                                             */
    /* ------------------------------------------------------------------ */
    @Override
    public String toString() {
        return getName() + " | " + getType()
                + (extraValue != null ? " | Extra: " + extraValue : "");
    }
}
