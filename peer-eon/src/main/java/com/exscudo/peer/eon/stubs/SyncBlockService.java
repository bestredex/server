package com.exscudo.peer.eon.stubs;

import java.io.IOException;
import java.util.ArrayList;

import com.exscudo.peer.core.data.Block;
import com.exscudo.peer.core.data.Difficulty;
import com.exscudo.peer.core.exceptions.RemotePeerException;
import com.exscudo.peer.core.services.IBlockSynchronizationService;
import com.exscudo.peer.core.services.IBlockchainService;
import com.exscudo.peer.core.utils.Format;
import com.exscudo.peer.eon.ExecutionContext;

/**
 * Basic implementation of the {@code IBlockSynchronizationService} interface
 *
 */
public class SyncBlockService extends BaseService implements IBlockSynchronizationService {
	/**
	 * The maximum number of blocks transmitted during synchronization.
	 */
	public static final int BLOCK_LIMIT = 10;

	private final ExecutionContext context;

	public SyncBlockService(ExecutionContext context) {
		this.context = context;
	}

	@Override
	public Difficulty getDifficulty() throws RemotePeerException, IOException {
		Block lastBlock = context.getInstance().getBlockchainService().getLastBlock();
		return new Difficulty(lastBlock.getID(), lastBlock.getCumulativeDifficulty());
	}

	@Override
	public Block[] getBlockHistory(String[] blockSequence) throws RemotePeerException, IOException {

		IBlockchainService blockchain = context.getInstance().getBlockchainService();

		long lastBlockID = blockchain.getLastBlock().getID();
		long commonBlockID = 0;

		try {

			int commonBlockHeight = -1;
			for (String encodedID : blockSequence) {
				long id = Format.ID.blockId(encodedID);
				int height = blockchain.getBlockHeight(id);
				if (height > commonBlockHeight) {
					commonBlockHeight = height;
					commonBlockID = id;
				}
			}

		} catch (IllegalArgumentException e) {
			throw new RemotePeerException("Unsupported request. Invalid transaction ID format.", e);
		}

		if (lastBlockID != blockchain.getLastBlock().getID()) {
			throw new RemotePeerException("Last block changed");
		}

		if (commonBlockID != 0) {

			ArrayList<Block> nextBlocks = new ArrayList<>();
			long id = commonBlockID;
			Block block = blockchain.getBlock(id);
			while (nextBlocks.size() < BLOCK_LIMIT && block.getNextBlock() != 0) {

				block = blockchain.getBlock(block.getNextBlock());

				if (block == null) {
					break;
				}

				if (lastBlockID != blockchain.getLastBlock().getID()) {
					break;
				}

				nextBlocks.add(block);
			}
			return nextBlocks.toArray(new Block[0]);

		}

		return new Block[0];

	}

	@Override
	public Block getLastBlock() throws RemotePeerException, IOException {
		return context.getInstance().getBlockchainService().getLastBlock();
	}

}
