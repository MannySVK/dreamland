package quests.Q900_GetASubclass;

import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.Quest;
import net.sf.l2j.gameserver.model.quest.QuestState;

public class Q900_GetASubclass extends Quest
{
	private static final String qn = "Q900_GetASubclass";
	
	// NPC
	private static final int CLASS_MANAGER = 50006;
	
	// Items
	private static final int SUBCLASS_MARK = 5011;
	
	public Q900_GetASubclass(int questId, String name, String descr)
	{
		super(questId, name, descr);
		
		addStartNpc(CLASS_MANAGER);
		addTalkId(CLASS_MANAGER);
	}
	
	@Override
	public String onTalk(L2Npc npc, L2PcInstance player)
	{
		String htmltext = getNoQuestMsg();
		QuestState st = player.getQuestState(qn);
		if (st == null)
			return htmltext;
		
		switch (st.getState())
		{
			case Quest.STATE_CREATED:
				if (player.getLevel() < 76)
				{
					player.sendMessage("Low lvl, canceling subclass quest.");
					st.exitQuest(true);
				}
				else if (player.getClassIndex() != 0)
				{
					player.sendMessage("Not a base class, canceling subclass quest.");
					st.exitQuest(true);
				}
				else
				{
					st.setState(STATE_STARTED);
					htmltext = "<html><body>Subclass quest started.</body></html>";
				}
				break;
			
			case Quest.STATE_STARTED:
				st.giveItems(SUBCLASS_MARK, 1);
				st.exitQuest(false);
				htmltext = "<html><body>Subclass quest completed.</body></html>";
				break;
				
			case Quest.STATE_COMPLETED:
				htmltext = getAlreadyCompletedMsg();
				break;
		}
		
		return htmltext;
	}
	
	public static void main(String[] args)
	{
		new Q900_GetASubclass(900, qn, "Get a Subclass");
	}
}