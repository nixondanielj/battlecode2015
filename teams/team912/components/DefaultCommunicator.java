package team912.components;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

class DefaultCommunicator extends Component implements Communicator {

	private int unitBlockStart;
	
	private final int RESERVED_CHANNELS = 20;
	
	// unit block
	private final int UNIT_BLOCK_WIDTH = 20;
	private final int OFFSET_UNITID = 0;
	private final int OFFSET_STATUS = 1;
	// note, a given squad's id is equal to that squad's block start
	private final int OFFSET_SQUAD_ID = 2;
	private final int OFFSET_MOVE_TARGET_Y = 3;
	private final int OFFSET_MOVE_TARGET_X = 4;
	private final int OFFSET_COMMAND = 5;
	
	// squad block
	private final int OFFSET_SQUAD_LEADER = 0;
	private final int SQUAD_BLOCK_START = 50000;
	private final int SQUAD_BLOCK_WIDTH = 20;

	public DefaultCommunicator(RobotController control) {
		super(control);
		init();
	}
	
	/**
	 * allocates a channel for this unit's communication
	 */
	private void init(){
		// TODO ensure sufficient bytecode is available to complete block allocation
		try {
			unitBlockStart = findOrAllocateBlock(getControl().getID());
		} catch (GameActionException e) {
			System.out.println("COMMUNICATOR EXPLODED VIOLENTLY");
			e.printStackTrace();
		}
	}
	
	// finds the current unit's squad block
	private int getMySquadId() throws GameActionException{
		return read(unitBlockStart + OFFSET_SQUAD_ID);
	}
	
	// finds an existing block for the specified unit
	// if a block does not exist, allocates one
	private int findOrAllocateBlock(int unitId) throws GameActionException{
		// checks the number of allocated blocks 
		int allocatedBlocks = getControl().readBroadcast(Channels.UnitBlockAllocationCount);
		for(int i = 0; i < allocatedBlocks; i++){
			int blockStart = i * UNIT_BLOCK_WIDTH + RESERVED_CHANNELS;
			int id = getControl().readBroadcast(blockStart + OFFSET_UNITID);
			if(id == unitId){
				return blockStart;
			}
		}
		// if we get here, there is no allocated block and we need to allocate
		
		// increments allocated blocks to avoid overwrite
		send(Channels.UnitBlockAllocationCount, allocatedBlocks + 1);
		unitBlockStart = allocatedBlocks * UNIT_BLOCK_WIDTH + RESERVED_CHANNELS;
		send(unitBlockStart + OFFSET_UNITID, unitId);
		return unitBlockStart;
	}

	@Override
	public void createSquad() {
		try{
			int allocatedBlocks = getControl().readBroadcast(Channels.SquadBlockAllocationCount);
			getControl().broadcast(Channels.SquadBlockAllocationCount, allocatedBlocks + 1);
			int squadBlockStart = allocatedBlocks * SQUAD_BLOCK_WIDTH + SQUAD_BLOCK_START;
			// sets the newly created squad's leader to this unit
			send(squadBlockStart + OFFSET_SQUAD_LEADER, getControl().getID());
			send(unitBlockStart + OFFSET_SQUAD_ID, squadBlockStart);
		} catch (GameActionException e){
			System.out.println("This should never happen");
			e.printStackTrace();
		}
	}

	@Override
	public List<Integer> getSquadMembers() throws GameActionException {
		// TODO add a "squads version" to the reserved block
		// will allow this "squadMember" data to be safely stored
		// and only renewed when the version increments
		List<Integer> squadMembers = new ArrayList<Integer>();
		int allocatedBlocks = read(Channels.UnitBlockAllocationCount);
		int mySquad = getMySquadId();
		for(int start = 0; start <= allocatedBlocks; start++) {
			int unitStart = start * UNIT_BLOCK_WIDTH + RESERVED_CHANNELS;
			// if the two units are in the same squad
			if(read(unitStart + OFFSET_SQUAD_ID) == mySquad){
				squadMembers.add(read(unitStart + OFFSET_UNITID));
			}
		}
		return squadMembers;
	}

	@Override
	public void addSquadMember(int robotId) {
		try {
			int memberBlock = findOrAllocateBlock(robotId);
			send(memberBlock + OFFSET_SQUAD_ID, getMySquadId());
		} catch (GameActionException e) {
			System.out.println("FAILED TO ADD SQUAD MEMBER");
			e.printStackTrace();
		}
	}

	@Override
	public void setMyStatus(UnitStatus status) {
		try {
			send(unitBlockStart + OFFSET_STATUS, status.ordinal());
		} catch (GameActionException e) {
			System.out.println("FAILED TO SET STATUS");
			e.printStackTrace();
		}
	}

	@Override
	public MapLocation getMyMoveTarget() {
		MapLocation target = null;
		try{
			int x = read(unitBlockStart + OFFSET_MOVE_TARGET_X);
			int y = read(unitBlockStart + OFFSET_MOVE_TARGET_Y);
			if(x != 0 || y != 0){
				target = new MapLocation(x, y);
			}
		} catch (GameActionException e){
			System.out.println("FAILED TO RETRIEVE MOVE TARGET");
			e.printStackTrace();
		}
		return target;
	}

	@Override
	public void setMoveTarget(int robotId, MapLocation target) {
		try {
			int robotBlock = findOrAllocateBlock(robotId);
			send(robotBlock + OFFSET_MOVE_TARGET_Y, target.y);
			send(robotBlock + OFFSET_MOVE_TARGET_X, target.x);
		} catch (GameActionException e){
			System.out.println("FAILED TO SET MOVE TARGET");
			e.printStackTrace();
		}
	}

	@Override
	public void setSquadMoveTarget(MapLocation target) {
		try {
			for(int unitId : getSquadMembers()){
				setMoveTarget(unitId, target);
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void issueCommand(int unitId, Command command) {
		try {
			int unitBlock = findOrAllocateBlock(unitId);
			send(unitBlock + OFFSET_COMMAND, command.ordinal());
		} catch (GameActionException e) {
			System.out.println("FAILED TO ISSUE COMMAND");
			e.printStackTrace();
		}
	}

	@Override
	public Command getOrders() {
		try {
			int commandOrdinal = read(unitBlockStart + OFFSET_COMMAND);
			return Command.values()[commandOrdinal];
		} catch (GameActionException e){
			System.out.println("FAILED TO GET COMMAND");
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public UnitStatus getStatus(int unitId) {
		try{
			int statusOrdinal = read(unitBlockStart + OFFSET_STATUS);
			return UnitStatus.values()[statusOrdinal];
		} catch (GameActionException e){
			System.out.println("FAILED TO GET STATUS");
			e.printStackTrace();
		}
		return null;
	}
	
	private void send(int channel, int data) throws GameActionException{
		getControl().broadcast(channel, data);
	}
	
	private int read(int channel) throws GameActionException{
		return getControl().readBroadcast(channel);
	}

	
}
