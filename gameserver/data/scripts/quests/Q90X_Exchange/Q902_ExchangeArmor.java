package quests.Q90X_Exchange;

import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.gameserver.model.L2ItemInstance;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.Quest;
import net.sf.l2j.gameserver.model.quest.QuestState;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.templates.item.L2Armor;
import net.sf.l2j.gameserver.templates.item.L2Item;

public class Q902_ExchangeArmor extends Quest
{
	private static final String qn = "Q902_ExchangeArmor";
	
	private static final String HTML = "./data/scripts/quests/Q90X_Exchange/armor.htm";
	
	// table
	private static final String TABLE = "";
	
	// NPC
	private static final int BLACKSMITH = 50002;
	
	// Items
	private static final int[][] ITEMS =
	{
		{ 5776, 0, 6384, 0 },
		{ 5773, 0, 6384, 0 },
		{ 5767, 0, 6384, 0 },
		{ 2770, 0, 6384, 0 },
		{ 5775, 0, 6380, 0 },
		{ 5772, 0, 6380, 0 },
		{ 5766, 0, 6380, 0 },
		{ 5769, 0, 6380, 0 },
		{ 5774, 0, 6375, 0 },
		{ 5771, 0, 6375, 0 },
		{ 5765, 0, 6375, 0 },
		{ 5768, 0, 6375, 0 },
		{ 5788, 0, 6385, 0 },
		{ 5785, 0, 6385, 0 },
		{ 5779, 0, 6385, 0 },
		{ 5782, 0, 6385, 0 },
		{ 5787, 0, 6381, 0 },
		{ 5784, 0, 6381, 0 },
		{ 5778, 0, 6381, 0 },
		{ 5781, 0, 6381, 0 },
		{ 5786, 0, 6376, 0 },
		{ 5783, 0, 6376, 0 },
		{ 5777, 0, 6376, 0 },
		{ 5780, 0, 6376, 0 },
		{ 2419, 0, 6378, 0 },
		{ 2418, 0, 6378, 0 },
		{ 512, 0, 6378, 0 },
		{ 547, 0, 6378, 0 },
		{ 2419, 0, 6382, 0 },
		{ 2418, 0, 6382, 0 },
		{ 512, 0, 6382, 0 },
		{ 547, 0, 6382, 0 },
		{ 2419, 0, 6386, 0 },
		{ 2418, 0, 6386, 0 },
		{ 512, 0, 6386, 0 },
		{ 547, 0, 6386, 0 },
		{ 2409, 0, 6383, 0 },
		{ 2408, 0, 6383, 0 },
		{ 2407, 0, 6383, 0 },
		{ 2400, 2405, 6383, 0 },
		{ 2395, 0, 6379, 0 },
		{ 2394, 0, 6379, 0 },
		{ 2385, 2389, 6379, 0 },
		{ 2393, 0, 6379, 0 },
		{ 2383, 0, 6373, 6374 },
		{ 374, 0, 6373, 6374 },
		{ 365, 0, 6373, 0 },
		{ 388, 0, 6374, 0 },
		{ 2382, 0, 6373, 6374 },
		{ 2498, 0, 6377, 0 },
		{ 641, 0, 6377, 0 },
	};
	
	private static final Map<Integer, String> ICONS = new HashMap<>();
	
	public Q902_ExchangeArmor(int questId, String name, String descr)
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
			if (!(i instanceof L2Armor))
				continue;
			
			// filter A grade items
			if (i.getCrystalType() != L2Item.CRYSTAL_A)
				continue;
			
			// filter enchant level
			if (item.getEnchantLevel() != 10)
				continue;
			
			int id = i.getItemId();
			for (int[] data : ITEMS)
			{
				if (data[0] != id)
					continue;
				
				if (data[1] == 0)
				{
					sb.append(item.getName());
					break;
				}
				
				boolean dual = false;
				for (L2ItemInstance item2 : player.getInventory().getItemsByItemId(data[1]))
				{
					if (item2.getEnchantLevel() != 10)
						continue;
					
					sb.append(item.getName());
					dual = true;
				}
				
				if (dual)
					break;
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
		if (item.getEnchantLevel() != 10)
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
		ICONS.put(365,  "icon.armor_t74_u_i00");
		ICONS.put(374,  "icon.armor_t80_ul_i00");
		ICONS.put(388,  "icon.armor_t74_l_i00");
		ICONS.put(512,  "icon.armor_helmet_i00");
		ICONS.put(547,  "icon.armor_helmet_i00");
		ICONS.put(641,  "icon.shield_dark_crystal_shield_i00");
		ICONS.put(2382, "icon.armor_t77_ul_i00");
		ICONS.put(2383, "icon.armor_t83_ul_i00");
		ICONS.put(2385, "icon.armor_t75_u_i00");
		ICONS.put(2389, "icon.armor_t75_l_i00");
		ICONS.put(2393, "icon.armor_t78_ul_i00");
		ICONS.put(2394, "icon.armor_t81_ul_i00");
		ICONS.put(2395, "icon.armor_t84_ul_i00");
		ICONS.put(2400, "icon.armor_t79_u_i00");
		ICONS.put(2405, "icon.armor_t79_l_i00");
		ICONS.put(2407, "icon.armor_t76_ul_i00");
		ICONS.put(2408, "icon.armor_t82_ul_i00");
		ICONS.put(2409, "icon.armor_t85_ul_i00");
		ICONS.put(2418, "icon.armor_leather_helmet_i00");
		ICONS.put(2419, "icon.armor_leather_helmet_i00");
		ICONS.put(2498, "icon.shield_shield_of_nightmare_i00");
		ICONS.put(5765, "icon.armor_t74_g_i00");
		ICONS.put(5766, "icon.armor_t75_g_i00");
		ICONS.put(5767, "icon.armor_t76_g_i00");
		ICONS.put(5768, "icon.armor_t77_g_i00");
		ICONS.put(5769, "icon.armor_t78_g_i00");
		ICONS.put(5770, "icon.armor_t79_g_i00");
		ICONS.put(5771, "icon.armor_t80_g_i00");
		ICONS.put(5772, "icon.armor_t81_g_i00");
		ICONS.put(5773, "icon.armor_t82_g_i00");
		ICONS.put(5774, "icon.armor_t83_g_i00");
		ICONS.put(5775, "icon.armor_t84_g_i00");
		ICONS.put(5776, "icon.armor_t85_g_i00");
		ICONS.put(5777, "icon.armor_t74_b_i00");
		ICONS.put(5778, "icon.armor_t75_b_i00");
		ICONS.put(5779, "icon.armor_t76_b_i00");
		ICONS.put(5780, "icon.armor_t77_b_i00");
		ICONS.put(5781, "icon.armor_t78_b_i00");
		ICONS.put(5782, "icon.armor_t79_b_i00");
		ICONS.put(5783, "icon.armor_t80_b_i00");
		ICONS.put(5784, "icon.armor_t81_b_i00");
		ICONS.put(5785, "icon.armor_t82_b_i00");
		ICONS.put(5786, "icon.armor_t83_b_i00");
		ICONS.put(5787, "icon.armor_t84_b_i00");
		ICONS.put(5788, "icon.armor_t85_b_i00");
		ICONS.put(6373, "icon.armor_t88_u_i00");
		ICONS.put(6374, "icon.armor_t88_l_i00");
		ICONS.put(6375, "icon.armor_t88_g_i00");
		ICONS.put(6376, "icon.armor_t88_b_i00");
		ICONS.put(6377, "icon.shield_imperial_crusader_shield_i00");
		ICONS.put(6378, "icon.armor_helmet_i00");
		ICONS.put(6379, "icon.armor_t89_ul_i00");
		ICONS.put(6380, "icon.armor_t89_g_i00");
		ICONS.put(6381, "icon.armor_t89_b_i00");
		ICONS.put(6382, "icon.armor_leather_helmet_i00");
		ICONS.put(6383, "icon.armor_t90_ul_i00");
		ICONS.put(6384, "icon.armor_t90_g_i00");
		ICONS.put(6385, "icon.armor_t90_b_i00");
		ICONS.put(6386, "icon.armor_leather_helmet_i00");
	}
	
	public static void main(String[] args)
	{
		new Q902_ExchangeArmor(902, qn, "Exchange Armor");
	}
}
