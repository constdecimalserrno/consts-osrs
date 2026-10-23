package com.constsexp;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

/**
 * The Forestry events, each with the NPCs or objects that mark it on screen. The markers are the ones RuneLite's own
 * Woodcutting plugin announces events by. An event starts when its first marker appears.
 */
enum ForestryEvent
{
    RISING_ROOTS("Roots", ConstsExpConfig::forestryRisingRoots,
        new int[0],
        new int[]{ObjectID.GATHERING_EVENT_RISING_ROOTS, ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL}),
    // The saved saplings are left out: once the sapling is saved the event is over.
    STRUGGLING_SAPLING("Sapling", ConstsExpConfig::forestryStrugglingSapling,
        new int[0],
        new int[]{
            ObjectID.GATHERING_EVENT_SAPLING_1X1, ObjectID.GATHERING_EVENT_SAPLING_WITHERING_1X1,
            ObjectID.GATHERING_EVENT_SAPLING_2X2, ObjectID.GATHERING_EVENT_SAPLING_WITHERING_2X2,
            ObjectID.GATHERING_EVENT_SAPLING_3X3, ObjectID.GATHERING_EVENT_SAPLING_WITHERING_3X3}),
    FLOWERING_TREE("Flowers", ConstsExpConfig::forestryFloweringTree,
        new int[]{
            NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01, NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02,
            NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03, NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04,
            NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05, NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06,
            NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07, NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08},
        new int[0]),
    LEPRECHAUN("Leprechaun", ConstsExpConfig::forestryLeprechaun,
        new int[]{NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN},
        new int[0]),
    BEEHIVE("Beehive", ConstsExpConfig::forestryBeehive,
        new int[]{NpcID.GATHERING_EVENT_BEES_BEEBOX_4},
        new int[0]),
    FRIENDLY_ENT("Ent", ConstsExpConfig::forestryFriendlyEnt,
        new int[]{NpcID.GATHERING_EVENT_ENTLINGS_NPC_01},
        new int[0]),
    POACHERS("Poachers", ConstsExpConfig::forestryPoachers,
        new int[]{NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS, NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS},
        new int[0]),
    PHEASANT_CONTROL("Pheasants", ConstsExpConfig::forestryPheasantControl,
        new int[]{NpcID.GATHERING_EVENT_PHEASANT_FORESTER},
        new int[0]),
    ENCHANTMENT_RITUAL("Ritual", ConstsExpConfig::forestryEnchantmentRitual,
        new int[]{NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD},
        new int[0]);

    private static final Map<Integer, ForestryEvent> BY_NPC_ID = new HashMap<>();
    private static final Map<Integer, ForestryEvent> BY_OBJECT_ID = new HashMap<>();

    static
    {
        for (ForestryEvent event : values())
        {
            for (int id : event.npcIds)
            {
                BY_NPC_ID.put(id, event);
            }
            for (int id : event.objectIds)
            {
                BY_OBJECT_ID.put(id, event);
            }
        }
    }

    private final String label;
    private final Predicate<ConstsExpConfig> wanted;
    private final int[] npcIds;
    private final int[] objectIds;

    ForestryEvent(String label, Predicate<ConstsExpConfig> wanted, int[] npcIds, int[] objectIds)
    {
        this.label = label;
        this.wanted = wanted;
        this.npcIds = npcIds;
        this.objectIds = objectIds;
    }

    /** The short name the status panel shows while this event's alert is firing. */
    String getLabel()
    {
        return label;
    }

    /** The event's own checkbox in the Woodcutting section. */
    boolean isWanted(ConstsExpConfig config)
    {
        return wanted.test(config);
    }

    /** The event an NPC marks, or null. */
    static ForestryEvent forNpc(int npcId)
    {
        return BY_NPC_ID.get(npcId);
    }

    /** The event an object marks, or null. */
    static ForestryEvent forObject(int objectId)
    {
        return BY_OBJECT_ID.get(objectId);
    }
}
