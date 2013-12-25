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

public class Q904_ExchangeJewelery extends Quest
{
	private static final String qn = "Q904_ExchangeJewelery";
	
	private static final String HTML = "./data/scripts/quests/Q90X_Exchange/jewelery.htm";
	
	private static final String TABLE_LINE = "<tr><td><img src=\"%A%\" width=32 height=32></td><td><button value=\"Exchange\" action=\"bypass -h Quest Q904_ExchangeJewelery exchange %B%\" width=75 height=21  fore=\"L2UI_ch3.Btn1_normal\" back=\"L2UI_ch3.Btn1_normalOn\"></td><td><img src=\"%C%\" width=32 height=32></td></tr><tr></tr>";
	
	private static final Map<Integer, String> ICONS = new HashMap<>();
	
	// NPC
	private static final int BLACKSMITH = 50002;
	
	// Items
	private static final int[][] ITEMS =
	{
		{ 893 , 889 },
		{ 862 , 858 },
		{ 924 , 920 },
	};
	
	public Q904_ExchangeJewelery(int questId, String name, String descr)
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
		ICONS.put( 924, "icon.accessary_inferno_necklace_i00" );
		ICONS.put( 862, "icon.accessary_inferno_earing_i00" );
		ICONS.put( 893, "icon.accessary_inferno_ring_i00" );
		ICONS.put( 889, "icon.accessory_tateossian_ring_i00" );
		ICONS.put( 858, "icon.accessory_tateossian_earring_i00" );
		ICONS.put( 920, "icon.accessory_tateossian_necklace_i00" );
	}
	
	public static void main(String[] args)
	{
		new Q904_ExchangeJewelery(904, qn, "Exchange Jewelery");
	}
}
