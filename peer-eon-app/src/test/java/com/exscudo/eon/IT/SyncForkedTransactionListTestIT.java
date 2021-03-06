package com.exscudo.eon.IT;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;

import com.exscudo.peer.core.Constant;
import com.exscudo.peer.core.IPeer;
import com.exscudo.peer.core.data.Block;
import com.exscudo.peer.core.data.Transaction;
import com.exscudo.peer.core.utils.Format;
import com.exscudo.peer.eon.TimeProvider;
import com.exscudo.peer.eon.tasks.SyncForkedTransactionListTask;
import com.exscudo.peer.eon.transactions.Payment;

@SuppressWarnings("WeakerAccess")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SyncForkedTransactionListTestIT {

	private static String GENERATOR = "55373380ff77987646b816450824310fb377c1a14b6f725b94382af3cf7b788a";
	private static String GENERATOR2 = "dd6403d520afbfadeeff0b1bb49952440b767663454ab1e5f1a358e018cf9c73";
	private TimeProvider mockTimeProvider;

	private PeerContext ctx1;
	private PeerContext ctx2;

	@Before
	public void setUp() throws Exception {
		mockTimeProvider = Mockito.mock(TimeProvider.class);
		ctx1 = new PeerContext(GENERATOR, mockTimeProvider);
		ctx2 = new PeerContext(GENERATOR2, mockTimeProvider);

		ctx1.syncBlockPeerService = Mockito.spy(ctx1.syncBlockPeerService);
		ctx2.syncBlockPeerService = Mockito.spy(ctx2.syncBlockPeerService);

		ctx1.setPeerToConnect(ctx2);
		ctx2.setPeerToConnect(ctx1);

	}

	@Test
	public void step_1_too_many_blocks() throws Exception {

		Block lastBlock = ctx1.context.getInstance().getBlockchainService().getLastBlock();

		int time = lastBlock.getTimestamp() + Constant.BLOCK_PERIOD * (Constant.BLOCK_IN_DAY + 2) + 1;
		Mockito.when(mockTimeProvider.get()).thenReturn(time);

		Thread thread1 = new Thread(() -> ctx1.generateBlockForNow());
		Thread thread2 = new Thread(() -> ctx2.generateBlockForNow());

		thread1.start();
		thread2.start();

		thread1.join();
		thread2.join();

		ctx1.syncBlockListTask.run();
		ctx2.syncBlockListTask.run();

		Assert.assertNotEquals("Blockchain not synchronized",
				ctx1.context.getInstance().getBlockchainService().getLastBlock().getID(),
				ctx2.context.getInstance().getBlockchainService().getLastBlock().getID());

		lastBlock = ctx1.context.getInstance().getBlockchainService().getLastBlock();
		Mockito.when(mockTimeProvider.get()).thenReturn(lastBlock.getTimestamp() + Constant.BLOCK_PERIOD + 1);

		Transaction tx1 = Payment.newPayment(10000L, Format.MathID.pick(ctx2.getSigner().getPublicKey())).forFee(1L)
				.validity(lastBlock.getTimestamp() + 150, 3600).build(ctx1.getSigner());
		Transaction tx2 = Payment.newPayment(10000L, Format.MathID.pick(ctx2.getSigner().getPublicKey())).forFee(1L)
				.validity(lastBlock.getTimestamp() + 100, 3600).build(ctx1.getSigner());

		ctx1.transactionBotService.putTransaction(tx1);
		ctx2.transactionBotService.putTransaction(tx2);

		ctx1.generateBlockForNow();
		ctx2.generateBlockForNow();

		Mockito.when(mockTimeProvider.get()).thenReturn(lastBlock.getTimestamp() + Constant.BLOCK_PERIOD * 2 + 1);

		SyncForkedTransactionListTask task1 = new SyncForkedTransactionListTask(ctx1.context);
		SyncForkedTransactionListTask task2 = new SyncForkedTransactionListTask(ctx2.context);

		IPeer peer1 = ctx1.context.getAnyConnectedPeer();
		ctx1.context.disablePeer(peer1);

		IPeer peer2 = ctx2.context.getAnyConnectedPeer();
		ctx2.context.disablePeer(peer2);

		task1.run();
		task2.run();

		Assert.assertTrue(
				ctx1.context.getInstance().getBlockchainService().transactionMapper().containsTransaction(tx1.getID()));
		Assert.assertTrue(
				ctx2.context.getInstance().getBlockchainService().transactionMapper().containsTransaction(tx2.getID()));

		Assert.assertTrue(ctx1.context.getInstance().getBacklogService().contains(tx2.getID()));
		Assert.assertTrue(ctx2.context.getInstance().getBacklogService().contains(tx1.getID()));
	}

}
