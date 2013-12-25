package quests.Q90X_Exchange;

import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.gameserver.model.L2ItemInstance;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.Quest;
import net.sf.l2j.gameserver.model.quest.QuestState;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.templates.item.L2Item;
import net.sf.l2j.gameserver.templates.item.L2Weapon;

public class Q903_ExchangeWeapon extends Quest
{
	private static final String qn = "Q903_ExchangeWeapon";
	
	private static final String HTML = "./data/scripts/quests/Q90X_Exchange/weapon.htm";
	
	private static final String TABLE_LINE = "<tr><td><img src=\"%A%\" width=32 height=32></td><td><button value=\"Exchange\" action=\"bypass -h Quest Q903_ExchangeWeapon exchange %B%\" width=75 height=21  fore=\"L2UI_ch3.Btn1_normal\" back=\"L2UI_ch3.Btn1_normalOn\"></td><td><img src=\"%C%\" width=32 height=32></td></tr><tr></tr>";
	
	private static final Map<Integer, String> ICONS = new HashMap<>();
	
	// NPC
	private static final int BLACKSMITH = 50002;
	
	// Items
	private static final int[][] ITEMS =
	{
		{ 80, 6364 },
		{ 5636, 6364 },
		{ 5637, 6364 },
		{ 4720, 6364 },
		{ 2500, 6364 },
		{ 5647, 6364 },
		{ 5648, 6364 },
		{ 5649, 6364 },
		{ 150, 9600 },
		{ 5640, 9600 },
		{ 5638, 9600 },
		{ 5639, 9600 },
		{ 151, 9600 },
		{ 5643, 9600 },
		{ 5641, 9600 },
		{ 5642, 9600 },
		{ 7884, 6372 },
		{ 8108, 6372 },
		{ 8110, 6372 },
		{ 8109, 6372 },
		{ 81, 6372 },
		{ 5645, 6372 },
		{ 5646, 6372 },
		{ 5644, 6372 },
		{ 2504, 6365 },
		{ 4757, 6365 },
		{ 4756, 6365 },
		{ 5600, 6365 },
		{ 164, 6365 },
		{ 5603, 6365 },
		{ 5602, 6365 },
		{ 5604, 6365 },
		{ 7894, 6579 },
		{ 8149, 6579 },
		{ 8147, 6579 },
		{ 8148, 6579 },
		{ 7895, 6579 },
		{ 8150, 6579 },
		{ 8151, 6579 },
		{ 8152, 6579 },
		{ 7899, 6369 },
		{ 8128, 6369 },
		{ 8127, 6369 },
		{ 8126, 6369 },
		{ 7902, 6369 },
		{ 8136, 6369 },
		{ 8135, 6369 },
		{ 8137, 6369 },
		{ 212, 6366 },
		{ 5596, 6366 },
		{ 5598, 6366 },
		{ 5597, 6366 },
		{ 213, 6366 },
		{ 5607, 6366 },
		{ 5605, 6366 },
		{ 5606, 6366 },
		{ 235, 6367 },
		{ 4784, 6367 },
		{ 4783, 6367 },
		{ 4785, 6367 },
		{ 236, 6367 },
		{ 5618, 6367 },
		{ 5617, 6367 },
		{ 5619, 6367 },
		{ 288, 7575 },
		{ 4833, 7575 },
		{ 4832, 7575 },
		{ 5625, 7575 },
		{ 289, 7575 },
		{ 5611, 7575 },
		{ 5612, 7575 },
		{ 5613, 7575 },
		{ 269, 6371 },
		{ 4809, 6371 },
		{ 5621, 6371 },
		{ 4807, 6371 },
		{ 270, 6371 },
		{ 5623, 6371 },
		{ 5624, 6371 },
		{ 5624, 6371 },
		{ 98, 6370 },
		{ 4862, 6370 },
		{ 4861, 6370 },
		{ 4863, 6370 },
		{ 305, 6370 },
		{ 5633, 6370 },
		{ 5632, 6370 },
		{ 5634, 6370 },
		{ 5233, 6580 },
		{ 5705, 6580 },
		{ 5706, 6580 },
	};
	
	public Q903_ExchangeWeapon(int questId, String name, String descr)
	{
		super(questId, name, descr);
		
		addStartNpc(BLACKSMITH);
		addTalkId(BLACKSMITH);
		
		load();
	}
	
	@Override
	public String onTalk(L2Npc npc, L2PcInstance player)
	{
		String htmltext = getNoQuestMsg();
		QuestState st = player.getQuestState(qn);
		if (st == null)
			return htmltext;
		
		NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setFile(HTML);
		
		StringBuffer sb = new StringBuffer(20);
		for (L2ItemInstance item : player.getInventory().getItems())
		{
			L2Item i = item.getItem();
			
			// filter armors
			if (!(i instanceof L2Weapon))
				continue;
			
			// filter A grade items
			if (i.getCrystalType() != L2Item.CRYSTAL_A)
				continue;
			
			// filter enchant level
			if (item.getEnchantLevel() < 10)
				continue;
			
			int id = i.getItemId();
			for (int[] data : ITEMS)
			{
				if (data[0] != id)
					continue;
				
				String a = TABLE_LINE.replace("%A%", ICONS.get(id)).replace("%B%", String.valueOf(item.getObjectId())).replace("%C%", ICONS.get(data[1]));
				sb.append(a);
			}
		}
		html.replace("%ITEMS%", sb.toString());
		
		player.sendPacket(html);
		
		return null;
	}

	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		String htmltext = event;
		QuestState st = player.getQuestState(qn);
		if (st == null)
			return htmltext;
		
		if (event.startsWith("exchange"))
		{
			exchange(player, Integer.parseInt(event.substring(9)));
		}
		
		return null;
	}
	
	private static final boolean exchange(L2PcInstance player, int objectId)
	{
		L2ItemInstance item = player.getInventory().getItemByObjectId(objectId);
		if (item == null)
			return false;
		
		// check enchant level
		if (item.getEnchantLevel() < 10)
			return false;
		
		// check grade
		if (item.getItem().getCrystalType() != L2Item.CRYSTAL_A)
			return false;
		
		// find the replace item
		int id = item.getItemId();
		int exchange = 0;
		for (int[] itemData : ITEMS)
		{
			if (itemData[0] != id)
				continue;
			
			exchange = itemData[1];
			break;
		}
		
		// found replace item?
		if (exchange == 0)
			return false;
		
		// remove origin and add replace item
		if (player.destroyItem("ExchageRemove", item, player, true))
		{
			player.addItem("ExchangeAdd", exchange, 1, player, true);
			return true;
		}
		
		return false;
	}
	
	private static void load()
	{
		ICONS.put(80, "icon.weapon_tallum_blade_i00" );
		ICONS.put(5636, "icon.weapon_tallum_blade_i01" );
		ICONS.put(5637, "icon.weapon_tallum_blade_i01" );
		ICONS.put(4720, "icon.weapon_tallum_blade_i01" );
		ICONS.put(2500, "icon.weapon_dark_legions_edge_i00" );
		ICONS.put(5647, "icon.weapon_dark_legions_edge_i01" );
		ICONS.put(5648, "icon.weapon_dark_legions_edge_i01" );
		ICONS.put(5649, "icon.weapon_dark_legions_edge_i01" );
		ICONS.put(150, "icon.weapon_elemental_sword_i00" );
		ICONS.put(5640, "icon.weapon_elemental_sword_i01" );
		ICONS.put(5638, "icon.weapon_elemental_sword_i01" );
		ICONS.put(5639, "icon.weapon_elemental_sword_i01" );
		ICONS.put(151, "icon.weapon_sword_of_miracle_i00" );
		ICONS.put(5643, "icon.weapon_sword_of_miracle_i01" );
		ICONS.put(5641, "icon.weapon_sword_of_miracle_i01" );
		ICONS.put(5642, "icon.weapon_sword_of_miracle_i01" );
		ICONS.put(7884, "icon.weapon_inferno_master_i00" );
		ICONS.put(8108, "icon.weapon_inferno_master_i01" );
		ICONS.put(8110, "icon.weapon_inferno_master_i01" );
		ICONS.put(8109, "icon.weapon_inferno_master_i01" );
		ICONS.put(81, "icon.weapon_dragon_slayer_i00" );
		ICONS.put(5645, "icon.weapon_dragon_slayer_i01" );
		ICONS.put(5646, "icon.weapon_dragon_slayer_i01" );
		ICONS.put(5644, "icon.weapon_dragon_slayer_i01" );
		ICONS.put(2504, "icon.weapon_meteor_shower_i00" );
		ICONS.put(4757, "icon.weapon_meteor_shower_i01" );
		ICONS.put(4756, "icon.weapon_meteor_shower_i01" );
		ICONS.put(5600, "icon.weapon_meteor_shower_i01" );
		ICONS.put(164, "icon.weapon_elysian_i00" );
		ICONS.put(5603, "icon.weapon_elysian_i01" );
		ICONS.put(5602, "icon.weapon_elysian_i01" );
		ICONS.put(5604, "icon.weapon_elysian_i01" );
		ICONS.put(7894, "icon.weapon_eye_of_soul_i00" );
		ICONS.put(8149, "icon.weapon_eye_of_soul_i01" );
		ICONS.put(8147, "icon.weapon_eye_of_soul_i01" );
		ICONS.put(8148, "icon.weapon_eye_of_soul_i01" );
		ICONS.put(7895, "icon.weapon_dragon_flame_head_i00" );
		ICONS.put(8150, "icon.weapon_dragon_flame_head_i01" );
		ICONS.put(8151, "icon.weapon_dragon_flame_head_i01" );
		ICONS.put(8152, "icon.weapon_dragon_flame_head_i01" );
		ICONS.put(7899, "icon.weapon_hammer_of_destroyer_i00" );
		ICONS.put(8128, "icon.weapon_hammer_of_destroyer_i01" );
		ICONS.put(8127, "icon.weapon_hammer_of_destroyer_i01" );
		ICONS.put(8126, "icon.weapon_hammer_of_destroyer_i01" );
		ICONS.put(7902, "icon.weapon_doom_crusher_i00" );
		ICONS.put(8136, "icon.weapon_doom_crusher_i01" );
		ICONS.put(8135, "icon.weapon_doom_crusher_i01" );
		ICONS.put(8137, "icon.weapon_doom_crusher_i01" );
		ICONS.put(212, "icon.weapon_dasparions_staff_i00" );
		ICONS.put(5596, "icon.weapon_dasparions_staff_i01" );
		ICONS.put(5598, "icon.weapon_dasparions_staff_i01" );
		ICONS.put(5597, "icon.weapon_dasparions_staff_i01" );
		ICONS.put(213, "icon.weapon_worldtrees_branch_i00" );
		ICONS.put(5607, "icon.weapon_worldtrees_branch_i01" );
		ICONS.put(5605, "icon.weapon_worldtrees_branch_i01" );
		ICONS.put(5606, "icon.weapon_worldtrees_branch_i01" );
		ICONS.put(235, "icon.weapon_bloody_orchid_i00" );
		ICONS.put(4784, "icon.weapon_bloody_orchid_i01" );
		ICONS.put(4783, "icon.weapon_bloody_orchid_i01" );
		ICONS.put(4785, "icon.weapon_bloody_orchid_i01" );
		ICONS.put(236, "icon.weapon_soul_separator_i00" );
		ICONS.put(5618, "icon.weapon_soul_separator_i01" );
		ICONS.put(5617, "icon.weapon_soul_separator_i01" );
		ICONS.put(5619, "icon.weapon_soul_separator_i01" );
		ICONS.put(288, "icon.weapon_carnium_bow_i00" );
		ICONS.put(4833, "icon.weapon_carnium_bow_i01" );
		ICONS.put(4832, "icon.weapon_carnium_bow_i01" );
		ICONS.put(5625, "icon.weapon_dragon_grinder_i01" );
		ICONS.put(289, "icon.weapon_soul_bow_i00" );
		ICONS.put(5611, "icon.weapon_soul_bow_i01" );
		ICONS.put(5612, "icon.weapon_soul_bow_i01" );
		ICONS.put(5613, "icon.weapon_soul_bow_i01" );
		ICONS.put(269, "icon.weapon_blood_tornado_i00" );
		ICONS.put(4809, "icon.weapon_blood_tornado_i01" );
		ICONS.put(5621, "icon.weapon_blood_tornado_i01" );
		ICONS.put(4807, "icon.weapon_blood_tornado_i01" );
		ICONS.put(270, "icon.weapon_dragon_grinder_i00" );
		ICONS.put(5623, "icon.weapon_dragon_grinder_i01" );
		ICONS.put(5624, "icon.weapon_dragon_grinder_i01" );
		ICONS.put(5624, "icon.weapon_dragon_grinder_i01" );
		ICONS.put(98, "icon.weapon_halbard_i00" );
		ICONS.put(4862, "icon.weapon_halbard_i01" );
		ICONS.put(4861, "icon.weapon_halbard_i01" );
		ICONS.put(4863, "icon.weapon_halbard_i01" );
		ICONS.put(305, "icon.weapon_tallum_glaive_i00" );
		ICONS.put(5633, "icon.weapon_tallum_glaive_i01" );
		ICONS.put(5632, "icon.weapon_tallum_glaive_i01" );
		ICONS.put(5634, "icon.weapon_tallum_glaive_i01" );
		ICONS.put(5233, "icon.weapon_dual_sword_i00" );
		ICONS.put(5705, "icon.weapon_dual_sword_i00" );
		ICONS.put(5706, "icon.weapon_dual_sword_i00" );
		ICONS.put(6364, "icon.weapon_forgotten_blade_i00" );
		ICONS.put(6579, "icon.weapon_arcana_mace_i00" );
		ICONS.put(6372, "icon.weapon_heavens_divider_i00" );
		ICONS.put(6365, "icon.weapon_basalt_battlehammer_i00" );
		ICONS.put(6369, "icon.weapon_dragon_hunter_axe_i00" );
		ICONS.put(6366, "icon.weapon_imperial_staff_i00" );
		ICONS.put(6367, "icon.weapon_angel_slayer_i00" );
		ICONS.put(7575, "icon.weapon_draconic_bow_i00" );
		ICONS.put(6371, "icon.weapon_demon_splinter_i00" );
		ICONS.put(6370, "icon.weapon_saint_spear_i00" );
		ICONS.put(6580, "icon.weapon_dual_sword_i00" );
		ICONS.put(9600, "L2Dreamland.weapon_blink_slasher_i00" );
	}
	
	public static void main(String[] args)
	{
		new Q903_ExchangeWeapon(903, qn, "Exchange Weapon");
	}
}
