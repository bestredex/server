package com.exscudo.eon.bot;

import java.io.IOException;
import java.util.*;

import com.exscudo.peer.core.data.Block;
import com.exscudo.peer.core.data.Transaction;
import com.exscudo.peer.core.exceptions.RemotePeerException;
import com.exscudo.peer.core.services.AccountProperty;
import com.exscudo.peer.core.services.IBacklogService;
import com.exscudo.peer.core.utils.Format;
import com.exscudo.peer.store.sqlite.ConnectionProxy;
import com.exscudo.peer.store.sqlite.Storage;
import com.exscudo.peer.store.sqlite.utils.BlockHelper;
import com.exscudo.peer.store.sqlite.utils.TransactionHelper;

/**
 * Account history service
 */
public class TransactionHistoryService {
	private final Storage storage;

	public TransactionHistoryService(Storage state) {
		this.storage = state;
	}

	/**
	 * Get committed transactions
	 * 
	 * @param id
	 *            account ID
	 * @return
	 * @throws RemotePeerException
	 * @throws IOException
	 */
	public List<Transaction> getCommitted(String id) throws RemotePeerException, IOException {
		long accID;
		try {
			accID = Format.ID.accountId(id);
		} catch (IllegalArgumentException e) {
			throw new RemotePeerException(e);
		}
		ConnectionProxy connection = storage.getConnection();

		Map<Long, Transaction> byRecipient = TransactionHelper.findByRecipient(connection, accID);
		Map<Long, Transaction> bySender = TransactionHelper.findBySender(connection, accID);

		HashMap<Long, Transaction> map = new HashMap<>();

		map.putAll(byRecipient);
		map.putAll(bySender);

		List<Transaction> list = new ArrayList<>(map.values());

		return list;
	}

	/**
	 * Get uncommitted transactions
	 * 
	 * @param id
	 *            account ID
	 * @return
	 * @throws RemotePeerException
	 * @throws IOException
	 *
	 * @see IBacklogService
	 */
	public List<Transaction> getUncommitted(String id) throws RemotePeerException, IOException {
		List<Transaction> items = new ArrayList<>();

		long accID;
		try {
			accID = Format.ID.accountId(id);
		} catch (IllegalArgumentException e) {
			throw new RemotePeerException(e);
		}

		IBacklogService backlog = storage.getBacklog();
		final Iterator<Long> indexes = backlog.iterator();

		while (indexes.hasNext()) {
			long item = indexes.next();
			Transaction transaction = backlog.get(item);
			if (transaction != null) {
				if (transaction.getSenderID() == accID) {
					items.add(transaction);
				} else if (transaction.getData() != null && id.equals(transaction.getData().get("recipient"))) {
					items.add(transaction);
				}
			}
		}

		return items;
	}

	/**
	 * Get committed blocks
	 * 
	 * @param id
	 *            account ID
	 * @return
	 * @throws RemotePeerException
	 * @throws IOException
	 */
	public List<Block> getSignedBlock(String id) throws RemotePeerException, IOException {
		List<Block> items = new ArrayList<>();
		ConnectionProxy connection = storage.getConnection();

		long accID;
		try {
			accID = Format.ID.accountId(id);
		} catch (IllegalArgumentException e) {
			throw new RemotePeerException(e);
		}

		Block lastBlock = this.storage.getLastBlock();

		long[] list = BlockHelper.getBlockListByPeerAccount(connection, accID,
				lastBlock.getTimestamp() - (24 * 60 * 60));
		for (long item : list) {
			Block b = BlockHelper.get(connection, item);

			if (b != null) {
				// Clear transactions and properties in block ti minimize package size
				b.setAccProps(new AccountProperty[0]);
				b.setTransactions(new ArrayList<>());
				items.add(b);
			}
		}

		return items;
	}
}
