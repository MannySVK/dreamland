/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ai.individual;

import java.util.List;
import java.util.logging.Logger;

import ai.AbstractNpcAI;

import net.sf.l2j.gameserver.GameTimeController;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager.L2GrandBossData;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Attackable;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.QuestEventType;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.util.Rnd;

public class Zaken extends AbstractNpcAI
{
	protected static final Logger log = Logger.getLogger(Zaken.class.getName());
	
	// Monsters
	private static final int ZAKEN = 45350;
	
	// States
	private static final byte DEAD = 0;
	private static final byte ALIVE = 1;
	
	// Respawn
	private static final int SPAWN_INTERVAL = 259200000; // 3 days
	private static final int SPAWN_WINDOW = 10800000; // 3 hours
	private static final int SPAWN_DELAY = 86400000; // 1 day
	
	// Timers
	private static final int TIMER_MAINTENANCE = 60000; // 1 minute
	private static final int TIMER_IDLE = 10; // 10 * maintenance
	
	// Skills
	private static final int SKILL_RETURN = 4222;
	private static final int SKILL_SCATTER_ENEMY = 4216;
	private static final int SKILL_REGENERATION_ON = 4227;
	private static final int SKILL_REGENERATION_OFF = 4242;
	private static final int SKILL_FACE_DAY = 4223;
	private static final int SKILL_FACE_NIGHT = 4224;
	
	// Other
	private static final byte HOURS_NIGHT = 24; // from 00:00 night (24 = midnight = 00:00am)
	private static final byte HOURS_DAY = 6; // from 06:00 day
	private static byte _teleOnAttack = 3; // 0.3%
	private static byte _teleOnSpell = 2; // 0.2%
	
	private byte _idle = 0;
	private static int _teleports = 0;
	
	private static final Location[] _locs = 
	{
		new Location(53920, 219810, -3488, 0), // 1st floor
		new Location(55980, 219820, -3488, 0),
		new Location(54950, 218790, -3488, 0),
		new Location(55970, 217770, -3488, 0),
		new Location(53930, 217760, -3488, 0),
		
		new Location(55970, 217770, -3216, 0), // 2nd floor
		new Location(55980, 219800, -3216, 0),
		new Location(54960, 218790, -3216, 0),
		new Location(53920, 219810, -3216, 0),
		new Location(53930, 217760, -3216, 0),
		
		new Location(55970, 217770, -2944, 0), // 3rd floor
		new Location(55970, 219820, -2944, 0),
		new Location(54960, 218790, -2944, 0),
		new Location(53920, 219810, -2944, 0),
		new Location(53930, 217760, -2944, 0),
	};
	
	private static final String[] MESSAGE_ON_RETURN =
	{
		"Fools, you think you can overtake me? I seriously doubt that!",
		"Bye fellas, you won't find me on my ship!",
		"Seriously, you ain't going to get me that easy.",
		"I've survived many curious adventurers...and I will survive even you.",
		"This has gone too far...",
	};
	
	private static final String[] MESSAGE_ON_SCATTER = 
	{
		"s1, how dare you attacking me!",
		"s1, you ain't giving up, are you?",
		"s1, here is your punishment for messing up with me!"
	};
	
	public Zaken(String name, String descr)
	{
		super(name, descr);
		
		addEventId(ZAKEN, QuestEventType.ON_ATTACK);
		addEventId(ZAKEN, QuestEventType.ON_SKILL_SEE);
		addEventId(ZAKEN, QuestEventType.ON_SPELL_FINISHED);
		addEventId(ZAKEN, QuestEventType.ON_KILL);
		
		L2GrandBossData data = GrandBossManager.getInstance().getBossData(ZAKEN);
		if (data.getState() == DEAD)
		{
			long temp = data.getRespawn() - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("zaken_unlock", temp, null, null, 0);
			else
				trySpawnBoss(data);
		}
		else
			spawnBoss(data);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		if (event.equalsIgnoreCase("zaken_maintenance"))
		{
			if (_idle > 0 && --_idle == 0)
			{
				npc.setTarget(npc);
				npc.doCast(SkillTable.getInstance().getInfo(SKILL_RETURN, 1));
				_idle = TIMER_IDLE;
			}
			
			if (npc.getCurrentHp() * 100 > npc.getMaxHp() * 90)
				_teleports = 3;
			
			GrandBossManager.getInstance().updateBossData(ZAKEN, true);
		}
		else if (event.equalsIgnoreCase("zaken_day"))
		{
			zakenSunrise();
		}
		else if (event.equalsIgnoreCase("zaken_night"))
		{
			zakenSunset();
		}
		else if (event.equalsIgnoreCase("zaken_unlock"))
		{
			trySpawnBoss(GrandBossManager.getInstance().getBossData(ZAKEN));
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onSkillSee(L2Npc npc, L2PcInstance caster, L2Skill skill, L2Object[] targets, boolean isPet)
	{
		if (isDayZaken() && !npc.isCastingNow() && Rnd.get(1000) < _teleOnSpell)
		{
			npc.setTarget(caster);
			npc.doCast(SkillTable.getInstance().getInfo(SKILL_SCATTER_ENEMY, 1));
		}
		return super.onSkillSee(npc, caster, skill, targets, isPet);
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isPet)
	{
		if (isDayZaken())
		{
			if (!npc.isCastingNow())
			{
				if (npc.getCurrentHp() * 4 < npc.getMaxHp() * _teleports)
				{
					_teleports--;
					npc.setTarget(npc);
					npc.doCast(SkillTable.getInstance().getInfo(SKILL_RETURN, 1));
					npc.broadcastNpcSay(MESSAGE_ON_RETURN[Rnd.get(MESSAGE_ON_RETURN.length)]);
				}
				else if (Rnd.get(1000) < _teleOnAttack)
				{
					npc.setTarget(attacker);
					npc.doCast(SkillTable.getInstance().getInfo(SKILL_SCATTER_ENEMY, 1));
					npc.broadcastNpcSay(MESSAGE_ON_SCATTER[Rnd.get(MESSAGE_ON_SCATTER.length)].replaceAll("s1", attacker.getName()));
				}				
			}
			_idle = TIMER_IDLE;
		}
		return super.onAttack(npc, attacker, damage, isPet);
	}
	
	@Override
	public String onSpellFinished(L2Npc npc, L2PcInstance player, L2Skill skill)
	{
		int skillId = skill.getId();
		if (skillId == SKILL_RETURN)
		{
			Location loc = _locs[Rnd.get(15)];
			npc.teleToLocation(loc.getX() + Rnd.get(600), loc.getY() + Rnd.get(600), loc.getZ(), 0);
			npc.getAI().setIntention(CtrlIntention.IDLE);
		}
		else if (skillId == SKILL_SCATTER_ENEMY)
		{
			Location loc = _locs[Rnd.get(15)];
			player.teleToLocation(loc.getX() + Rnd.get(600), loc.getY() + Rnd.get(600), loc.getZ(), 0);
			player.getAI().setIntention(CtrlIntention.IDLE);
			
			((L2Attackable) npc).stopHating(player);
			L2Character nextTarget = ((L2Attackable) npc).getMostHated();
			if (nextTarget != null)
				npc.getAI().setIntention(CtrlIntention.ATTACK, nextTarget);
			
		}
		return super.onSpellFinished(npc, player, skill);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		cancelQuestTimers("zaken_maintenance");
		cancelQuestTimers("zaken_day");
		cancelQuestTimers("zaken_night");

		int respawnTime = SPAWN_INTERVAL + Rnd.get(SPAWN_WINDOW);
		final L2GrandBossData data = GrandBossManager.getInstance().getBossData(ZAKEN);
		data.setRespawn(System.currentTimeMillis() + respawnTime);
		GrandBossManager.getInstance().updateBossState(ZAKEN, DEAD);
		GrandBossManager.getInstance().cleanClans(ZAKEN);
		// TODO: remove players from zone
		
		startQuestTimer("zaken_unlock", respawnTime, null, null, 0);
		
		npc.broadcastPacket(new PlaySound(1, "BS02_D", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));
		
		return super.onKill(npc, killer, isPet);
	}
	
	private static boolean isDayZaken()
	{
		int hours = (GameTimeController.getInstance().getGameTime() / 60) % 24;
		boolean result = hours >= HOURS_DAY && hours < HOURS_NIGHT;
		return result;
	}
	
	// TODO: RAID
	
	private void trySpawnBoss(L2GrandBossData data)
	{
		List<Integer> clans = data.getClans();
		if (clans.isEmpty())
		{
			long respawn = data.getRespawn();
			do
			{
				respawn += SPAWN_DELAY;
			}
			while (respawn < System.currentTimeMillis());
			
			data.setRespawn(respawn);
			GrandBossManager.getInstance().updateBossState(ZAKEN, DEAD);
			startQuestTimer("zaken_unlock", respawn - System.currentTimeMillis(), null, null, 0);
		}
		else
			spawnBoss(data);
	}
	
	private void spawnBoss(L2GrandBossData data)
	{
		L2GrandBossInstance raid;
		if (data.getState() == DEAD)
		{
			Location loc = _locs[Rnd.get(15)];
			raid = (L2GrandBossInstance) addSpawn(ZAKEN, loc.getX() + Rnd.get(600), loc.getY() + Rnd.get(600), loc.getZ(), Rnd.get(65536), false, 0, false);
			data.setInstance(raid);
			data.setState(ALIVE);
			GrandBossManager.getInstance().updateBossData(ZAKEN, true);
		}
		else
		{
			raid = (L2GrandBossInstance) addSpawn(ZAKEN, data.getLocation(), false, 0, false);
			raid.setCurrentHpMp(data.getCurrentHp(), data.getCurrentMp());
			data.setInstance(raid);
		}
		startQuestTimer("zaken_maintenance", TIMER_MAINTENANCE, raid, null, TIMER_MAINTENANCE);
		raid.broadcastPacket(new PlaySound(1, "BS01_A", 1, raid.getObjectId(), raid.getX(), raid.getY(), raid.getZ()));
		
		_idle = TIMER_IDLE;
		
		int time = GameTimeController.getInstance().getGameTime();
		int hours = (time / 60) % 24;
		int minutes = time % 60;
		
		// day handler
		int morning = (HOURS_DAY - hours) * 60 - minutes;
		if (morning < 0)
			morning += 1440;
		startQuestTimer("zaken_day", morning * 10000, null, null, 14400000);
		
		// night handler
		int evening = (HOURS_NIGHT - hours) * 60 - minutes;
		if (evening < 0)
			evening += 1440;
		startQuestTimer("zaken_night", evening * 10000, null, null, 14400000);
		
		// set zaken's day/night skills
		if (hours >= HOURS_DAY && hours < HOURS_NIGHT)
			zakenSunrise();
		else
			zakenSunset();
	}
	
	private static void zakenSunrise()
	{
		L2GrandBossData data = GrandBossManager.getInstance().getBossData(ZAKEN);
		if (data.getState() == ALIVE)
		{
			L2GrandBossInstance raid = data.getInstance();
			SkillTable.getInstance().getInfo(SKILL_REGENERATION_OFF, 1).getEffects(raid, raid);
			SkillTable.getInstance().getInfo(SKILL_FACE_DAY, 1).getEffects(raid, raid);
			_teleports = ((int) (raid.getCurrentHp() * 4)) / raid.getMaxHp();
		}
	}
	
	private static void zakenSunset()
	{
		L2GrandBossData data = GrandBossManager.getInstance().getBossData(ZAKEN);
		if (data.getState() == ALIVE)
		{
			L2GrandBossInstance raid = data.getInstance();
			SkillTable.getInstance().getInfo(SKILL_REGENERATION_ON, 1).getEffects(raid, raid);
			SkillTable.getInstance().getInfo(SKILL_FACE_NIGHT, 1).getEffects(raid, raid);
			_teleports = 0;
		}
	}
	
	public static void main(String[] args)
	{
		new Zaken(Zaken.class.getSimpleName(), "ai/individual");
	}
}