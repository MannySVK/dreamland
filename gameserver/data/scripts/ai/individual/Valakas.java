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

import ai.AbstractNpcAI;

import java.util.List;

import net.sf.l2j.gameserver.GeoData;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager.L2GrandBossData;
import net.sf.l2j.gameserver.model.L2CharPosition;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.L2Playable;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.QuestEventType;
import net.sf.l2j.gameserver.model.zone.type.L2BossZone;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.SpecialCamera;
import net.sf.l2j.gameserver.util.Util;
import net.sf.l2j.util.Rnd;

public class Valakas extends AbstractNpcAI
{
	public static Valakas _instance;
	
	// Monsters
	public static final int VALAKAS = 45650;
	
	// States
	public static final byte DEAD = 0; // Valakas has been killed. Entry is locked.
	public static final byte IDLE = 1; // Valakas is spawned and no one has entered yet. Entry is unlocked.
	public static final byte WAITING = 2; // Valakas is spawned and someone has entered, triggering a 30 minute window for additional people to enter. Entry is unlocked.
	public static final byte ALIVE = 3; // Valakas is engaged in battle, annihilating his foes. Entry is locked.
	
	// Spawn
	private static final int SPAWN_INTERVAL = 259200000; // 3 days
	private static final int SPAWN_WINDOW = 10800000; // 3 hours
	private static final int SPAWN_DELAY = 86400000; // 1 day
	public static final int SPAWN_WAIT = 300000; // 5 minutes
	private static final int ANIMATION_SPAWN = 10;
	private static final int ANIMATION_DIE = 20;
	
	// Timers
	private static final int TIMER_MAINTENANCE = 60000; // 1 minute
	private static final int TIMER_SKILL = 20000; // 20 seconds
	private static final int TIMER_IDLE = 15; // 15 * maintenance
	
	// Other
	private static final Location _loc = new Location(212960, -114740, -1635);
	private static L2BossZone _zone;
	private long _idle = 0;
	private int _animation = 0;
	private L2Playable _target;
	
	private static final int[] FRONT_SKILLS =
	{
		4681,
		4682,
		4683,
		4684,
		4689
	};
	
	private static final int[] BEHIND_SKILLS =
 	{
 		4685,
 		4686,
		4688
 	};
	
	private static final int LAVA_SKIN = 4680;
	private static final int METEOR_SWARM = 4690;
	
	public Valakas(String name, String descr)
	{
		super(name, descr);
		
		addEventId(VALAKAS, QuestEventType.ON_SPAWN);
		addEventId(VALAKAS, QuestEventType.ON_AGGRO_RANGE_ENTER);
		addEventId(VALAKAS, QuestEventType.ON_ATTACK);
		addEventId(VALAKAS, QuestEventType.ON_KILL);
		
		_zone = GrandBossManager.getInstance().getZoneByXYZ(212852, -114842, -1632);
		
		final L2GrandBossData data = GrandBossManager.getInstance().getBossData(VALAKAS);
		if (data.getState() == DEAD)
		{
			long temp = data.getRespawn() - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("valakas_unlock", temp, null, null, 0);
			else
				trySpawnBoss(data);
		}
		else
			spawnBoss(data);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		// Regeneration && inactivity task
		if (event.equalsIgnoreCase("valakas_maintenance"))
		{
			if (_idle > 0 && --_idle == 0)
			{
				_zone.oustAllPlayers();
				
				npc.deleteMe();
				GrandBossManager.getInstance().updateBossState(VALAKAS, IDLE);
				cancelQuestTimers("valakas_maintenance");
				cancelQuestTimers("valakas_skill");
				return null;
			}
			else
			{
				// FIXME: resolve via passive skill
				/*
				if (Rnd.get(30) == 0)
				{
					int level;
					final double hpRatio = npc.getCurrentHp() / npc.getMaxHp();
					
					if (hpRatio < 0.25)
						level = 4;
					else if (hpRatio < 0.5)
						level = 3;
					else if (hpRatio < 0.75)
						level = 2;
					else
						level = 1;
					
					SkillTable.getInstance().getInfo(4691, level).getEffects(npc, npc);
				}
				*/
				GrandBossManager.getInstance().updateBossData(VALAKAS, true);
			}
		}
		else if (event.equalsIgnoreCase("valakas_open"))
		{
			if (GrandBossManager.getInstance().getBossData(VALAKAS).getState() == IDLE)
			{
				GrandBossManager.getInstance().updateBossState(VALAKAS, WAITING);
				startQuestTimer("valakas_spawn", SPAWN_WAIT, null, null, 0);
			}
		}
		else if (event.equalsIgnoreCase("valakas_spawn"))
		{
			L2GrandBossData data = GrandBossManager.getInstance().getBossData(VALAKAS);
			final L2GrandBossInstance raid = (L2GrandBossInstance) addSpawn(VALAKAS, _loc, false, 0, false);
			data.setInstance(raid);
			GrandBossManager.getInstance().updateBossState(VALAKAS, ALIVE);
			
			raid.setIsInvul(true);
			raid.setIsImmobilized(true);
			
			_zone.broadcastPacket(new PlaySound(1, "B03_A", 0, 0, 0, 0, 0));
			_zone.broadcastPacket(new SocialAction(npc, 3));
			
			_animation = ANIMATION_SPAWN;
			startQuestTimer("valakas_animation", 1700, null, null, 0);			
			startQuestTimer("valakas_enable", 26000, raid, null, 0);
		}
		else if (event.equalsIgnoreCase("valakas_animation"))
		{
			switch (_animation--)
			{
				// Spawn
				case ANIMATION_SPAWN:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1800, 180, -1, 1500, 10000, 0, 0, 1, 0));
					startQuestTimer("valakas_animation", 1500, null, null, 0);
					break;
				case 9:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1300, 180, -5, 3000, 10000, 0, -5, 1, 0));
					startQuestTimer("valakas_animation", 3300, null, null, 0);
					break;
				case 8:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 500, 180, -8, 600, 10000, 0, 60, 1, 0));
					startQuestTimer("valakas_animation", 2900, null, null, 0);
					break;
				case 7:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 800, 180, -8, 2700, 10000, 0, 30, 1, 0));
					startQuestTimer("valakas_animation", 2700, null, null, 0);
					break;
				case 6:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 200, 250, 70, 0, 10000, 30, 80, 1, 0));
					startQuestTimer("valakas_animation", 330, null, null, 0);
					break;
				case 5:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1100, 250, 70, 2500, 10000, 30, 80, 1, 0));
					startQuestTimer("valakas_animation", 3000, null, null, 0);
					break;
				case 4:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 700, 150, 30, 0, 10000, -10, 60, 1, 0));
					startQuestTimer("valakas_animation", 1400, null, null, 0);
					break;
				case 3:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1200, 150, 20, 2900, 10000, -10, 30, 1, 0));
					startQuestTimer("valakas_animation", 6700, null, null, 0);
					break;
				case 2:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 750, 170, -10, 3400, 4000, 10, -15, 1, 0));
					_animation = 0;
					break;
				// Die
				case ANIMATION_DIE:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 2000, 130, -1, 0, 10000, 0, 0, 1, 1));
					startQuestTimer("valakas_animation", 300, npc, null, 0);
					break;
				case 19:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1100, 210, -5, 3000, 10000, -13, 0, 1, 1));
					startQuestTimer("valakas_animation", 3200, npc, null, 0);
					break;
				case 18:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1300, 200, -8, 3000, 10000, 0, 15, 1, 1));
					startQuestTimer("valakas_animation", 4400, npc, null, 0);
					break;
				case 17:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1000, 190, 0, 500, 10000, 0, 10, 1, 1));
					startQuestTimer("valakas_animation", 500, npc, null, 0);
					break;
				case 16:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1700, 120, 0, 2500, 10000, 12, 40, 1, 1));
					startQuestTimer("valakas_animation", 4600, npc, null, 0);
					break;
				case 15:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1700, 20, 0, 700, 10000, 10, 10, 1, 1));
					startQuestTimer("valakas_animation", 700, npc, null, 0);
					break;
				case 14:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1700, 10, 0, 1000, 10000, 20, 70, 1, 1));
					startQuestTimer("valakas_animation", 2500, npc, null, 0);
					break;
				case 13:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1700, 10, 0, 300, 250, 20, -20, 1, 1));
					_animation = 0;
					break;
			}
		}
		else if (event.equalsIgnoreCase("valakas_enable"))
		{
			npc.setIsInvul(false);
			npc.setIsImmobilized(false);
			npc.setRunning();
			
			_idle = TIMER_IDLE;
			startQuestTimer("valakas_maintenance", TIMER_MAINTENANCE, null, null, TIMER_MAINTENANCE);
			startQuestTimer("valakas_skill", TIMER_SKILL, npc, null, TIMER_SKILL);
		}
		else if (event.equalsIgnoreCase("valakas_skill"))
		{
			valakasSkill(npc);
		}
		else if (event.equalsIgnoreCase("valakas_unlock"))
		{
			trySpawnBoss(GrandBossManager.getInstance().getBossData(VALAKAS));
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onSpawn(L2Npc npc)
	{
		npc.disableCoreAI(true);
		return super.onSpawn(npc);
	}
	
	@Override
	public String onAggroRangeEnter(L2Npc npc, L2PcInstance player, boolean isPet)
	{
		return null;
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isPet)
	{
		_idle = TIMER_IDLE;
		
		return super.onAttack(npc, attacker, damage, isPet);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		cancelQuestTimers("valakas_maintenance");
		cancelQuestTimers("valakas_skill");
		
		int respawnTime = SPAWN_INTERVAL + Rnd.get(SPAWN_WINDOW);
		L2GrandBossData data = GrandBossManager.getInstance().getBossData(VALAKAS);
		data.setRespawn(System.currentTimeMillis() + respawnTime);
		GrandBossManager.getInstance().updateBossState(VALAKAS, DEAD);
		GrandBossManager.getInstance().cleanClans(VALAKAS);
		// TODO: remove players from zone
		
		startQuestTimer("valakas_unlock", respawnTime, null, null, 0);
		
		_zone.broadcastPacket(new PlaySound(1, "B03_D", 0, 0, 0, 0, 0));
		
		_animation = ANIMATION_DIE;
		startQuestTimer("valakas_animation", 300, npc, null, 0);
		
		return super.onKill(npc, killer, isPet);
	}
	
	private void valakasSkill(L2Npc npc)
	{
		if (npc.isInvul() || npc.isCastingNow())
			return;
		
		// Pickup a target if no or dead victim. 10% luck he decides to reconsiders his target.
		if (_target == null || _target.isDead() || !(npc.getKnownList().knowsObject(_target)) || Rnd.get(10) == 0)
			_target = getRandomPlayer(npc);
		
		// If result is still null, Valakas will roam. Don't go deeper in skill AI.
		if (_target == null)
		{
			if (Rnd.get(10) == 0)
			{
				int x = npc.getX();
				int y = npc.getY();
				int z = npc.getZ();
				
				int posX = x + Rnd.get(-1400, 1400);
				int posY = y + Rnd.get(-1400, 1400);
				
				if (GeoData.getInstance().canMoveFromToTarget(x, y, z, posX, posY, z))
					npc.getAI().setIntention(CtrlIntention.MOVE_TO, new L2CharPosition(posX, posY, z, 0));
			}
			return;
		}
		
		final L2Skill skill = SkillTable.getInstance().getInfo(getRandomSkill(npc), 1);
		
		// Cast the skill or follow the target.
		if (Util.checkIfInRange((skill.getCastRange() < 600) ? 600 : skill.getCastRange(), npc, _target, true))
		{
			npc.getAI().setIntention(CtrlIntention.IDLE);
			npc.setTarget(_target);
			npc.doCast(skill);
		}
		else
			npc.getAI().setIntention(CtrlIntention.FOLLOW, _target, null);
	}
	
	/**
	 * Pick a random skill.<br>
	 * Valakas will mostly use utility skills. If Valakas feels surrounded, he will use AoE skills.<br>
	 * Lower than 50% HPs, he will begin to use Meteor skill.
	 * @param npc valakas
	 * @return a usable skillId
	 */
	private int getRandomSkill(L2Npc npc)
	{
		final double hpRatio = npc.getCurrentHp() / npc.getMaxHp();
		
		// Valakas Lava Skin is prioritary.
		if (hpRatio < 0.25 && Rnd.get(1500) == 0 && npc.getFirstEffect(4680) == null)
			return LAVA_SKIN;
		
		// Valakas will use mass spells if he feels surrounded.
		if (hpRatio < 0.5 && Rnd.get(60) == 0)
			return METEOR_SWARM;
		
		// Find enemies surrounding Valakas.
		final int[] playersAround = getPlayersCountInPositions(1200, npc, false);
		
		// Behind position got more ppl than front position, use behind aura skill.
		if (playersAround[1] > playersAround[0])
			return BEHIND_SKILLS[Rnd.get(BEHIND_SKILLS.length)];
		
		// Use front aura skill.
		return FRONT_SKILLS[Rnd.get(FRONT_SKILLS.length)];
	}
	
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
			GrandBossManager.getInstance().updateBossState(VALAKAS, DEAD);
			startQuestTimer("valakas_unlock", respawn - System.currentTimeMillis(), null, null, 0);
		}
		else
			spawnBoss(data);
	}
	
	private void spawnBoss(L2GrandBossData data)
	{
		switch (data.getState())
		{
			case DEAD:
				GrandBossManager.getInstance().updateBossState(VALAKAS, IDLE);
			case IDLE:
				break;
			case WAITING:
				startQuestTimer("valakas_spawn", SPAWN_WAIT, null, null, 0);
				break;
			case ALIVE:
				final L2GrandBossInstance raid = (L2GrandBossInstance) addSpawn(VALAKAS, data.getLocation(), false, 0, false);
				raid.setCurrentHpMp(data.getCurrentHp(), data.getCurrentMp());
				raid.setRunning();
				data.setInstance(raid);
				
				_idle = TIMER_IDLE;
				startQuestTimer("valakas_maintenance", TIMER_MAINTENANCE, null, null, TIMER_MAINTENANCE);
				startQuestTimer("valakas_skill", TIMER_SKILL, raid, null, TIMER_SKILL);
				break;
		}
	}
	
	public static void main(String[] args)
	{
		_instance = new Valakas(Valakas.class.getSimpleName(), "ai/individual");
	}
}