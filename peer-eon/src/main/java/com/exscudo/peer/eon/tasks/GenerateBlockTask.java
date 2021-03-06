package com.exscudo.peer.eon.tasks;

import com.exscudo.peer.core.Constant;
import com.exscudo.peer.core.data.Block;
import com.exscudo.peer.core.data.Difficulty;
import com.exscudo.peer.core.exceptions.ValidateException;
import com.exscudo.peer.core.services.IBlockchainService;
import com.exscudo.peer.core.services.IUnitOfWork;
import com.exscudo.peer.core.utils.Format;
import com.exscudo.peer.core.utils.Loggers;
import com.exscudo.peer.eon.ExecutionContext;
import com.exscudo.peer.eon.Instance;

/**
 * Try to create a new block.
 * <p>
 * An attempt is made to generate a new ending for a chain of blocks. If a block
 * with highest difficulty was generated, then it is added to the end of chain.
 */
public final class GenerateBlockTask extends BaseTask implements Runnable {

	/**
	 * The last block generated by the node.
	 */
	private volatile Block lastGeneratedBlock = null;

	public GenerateBlockTask(ExecutionContext context) {
		super(context);
	}

	@Override
	public void run() {

		try {

			Instance instance = context.getInstance();
			if (!instance.getGenerator().isInitialized()) {
				// Generation at a node can be switched off...
				return;
			}

			IBlockchainService blockchain = instance.getBlockchainService();
			Block lastBlock = blockchain.getLastBlock();

			// Gives the opportunity to generate the best last block.
			Block lastCreatedBlock = lastGeneratedBlock;
			if (lastCreatedBlock == null || lastCreatedBlock.getHeight() == lastBlock.getHeight()) {
				lastCreatedBlock = lastBlock;
			} else {
				lastCreatedBlock = blockchain.getBlock(lastBlock.getPreviousBlock());
			}

			if (context.isCurrentForkPassed(lastCreatedBlock.getTimestamp() + Constant.BLOCK_PERIOD)) {
				return;
			}

			int elapsedTime = context.getCurrentTime() - lastCreatedBlock.getTimestamp();
			if (elapsedTime > Constant.BLOCK_PERIOD) {

				// New block creating...
				Block newBlock = instance.getGenerator().createNextBlock(lastCreatedBlock);
				if (newBlock == null) {
					Loggers.warning(GenerateBlockTask.class, "Unable to create new block.");
					return;
				}

				// The last blocks of the chain were removed after the block
				// creating was started.
				lastBlock = blockchain.getLastBlock();
				if (lastBlock.getHeight() < lastCreatedBlock.getHeight()) {

					Loggers.info(GenerateBlockTask.class,
							"Illegal state. lastCreatedBlock.getHeight() > lastBlock.getHeight().");
					return;

				}

				// Fixing the last block generated by the node.
				lastGeneratedBlock = newBlock;

				// Trying to add a new block to the end of the chain.
				Loggers.info(GenerateBlockTask.class, "Adding new block [{}] {}. Previous block [{}]{}",
						newBlock.getHeight(), Format.ID.blockId(newBlock.getID()), lastBlock.getHeight(),
						Format.ID.blockId(lastBlock.getID()));

				Difficulty currentState = new Difficulty(lastBlock);
				Difficulty newState = new Difficulty(newBlock);
				if (newState.compareTo(currentState) > 0) {

					IUnitOfWork uow = context.getInstance().getBlockchainService().beginPush(this, lastCreatedBlock);
					try {

						uow.pushBlock(newBlock);
						uow.commit();

					} catch (ValidateException e) {

						uow.rollback();
						Loggers.info(GenerateBlockTask.class, "Unable to add new block.", e);

					} catch (Exception e) {

						uow.rollback();
						throw e;

					}

				} else {
					// Could not create block with better cumulative
					// difficulty.
					Loggers.info(GenerateBlockTask.class, "Unable to add new block. Illegal state.");
				}

			}

		} catch (Exception e) {
			Loggers.error(GenerateBlockTask.class, e);
		}
	}

}