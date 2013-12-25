package quests.Q901_BecomeNobles;

import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.Quest;
import net.sf.l2j.gameserver.model.quest.QuestState;

public class Q901_BecomeNobles extends Quest
{
	private static final String qn = "Q901_BecomeNobles";
	
	// NPC
	private static final int CLASS_MANAGER = 50006;
	
	// Items
	private static final int NOBLESS_TIARA = 7694;
	
	public Q901_BecomeNobles(int questId, String name, String descr)
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
					player.sendMessage("Low lvl, canceling nobless quest.");
					st.exitQuest(true);
				}
				else if (player.getClassIndex() == 0)
				{
					player.sendMessage("Not a subclass, canceling subclass quest.");
					st.exitQuest(true);
				}
				else
				{
					st.setState(STATE_STARTED);
					htmltext = "<html><body>Nobles quest started.</body></html>";
				}
				break;
			
			case Quest.STATE_STARTED:
				player.setNoble(true, true);
				st.giveItems(NOBLESS_TIARA, 1);
				st.exitQuest(false);
				htmltext = "<html><body>Nobles quest completed.</body></html>";
				st.playSound(QuestState.SOUND_FANFARE);
				break;
				
			case Quest.STATE_COMPLETED:
				htmltext = getAlreadyCompletedMsg();
				break;
		}
		
		return htmltext;
	}
	
	public static void main(String[] args)
	{
		new Q901_BecomeNobles(901, qn, "Become Nobles");
	}
}
