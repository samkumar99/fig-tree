package figtree;

import java.util.*;

public class FigTree<V> {
	public FigTree(int order) {
		this.root = new FigTreeNode(0);
		this.ORDER = order;
		this.SPLITLIMIT = 1 + (order << 1);
	}
	
	public void insert(Interval range, V value) {
		ArrayList<FigTreeNode> path = new ArrayList<FigTreeNode>();
		ArrayList<Integer> pathIndices = new ArrayList<Integer>();
		FigTreeNode currnode = this.root;
		
		Interval valid = new Interval(Integer.MIN_VALUE, Integer.MAX_VALUE);
				
		/* We insert two intervals: range, and [range.right() + 1, star]. */
		int star = range.right();
		V starval = null;
		int numentries, i;
		
		outerloop:
			do {
				currnode.pruneTo(valid);
				numentries = currnode.numEntries();
				FigTreeEntry previous;
				FigTreeEntry current = null;
				Interval currival = null;
				for (i = 0; i < numentries; i++) {
					previous = current;
					current = currnode.entry(i);
					currival = current.interval();
					if (currival.leftOverlaps(range)) {
						// We need to replace all entries like this with a single entry for "range"
						int j;
						previous = current;
						for (j = i + 1; j < numentries && (current = currnode.entry(j)).interval().leftOverlaps(range); j++) {
							previous = current;
						}
						if (previous.interval().right() > star) {
							star = previous.interval().right();
							starval = previous.value();
						}
						currnode.replaceEntries(i, j, new FigTreeEntry(range, value));
						break outerloop;
					} else if (range.leftOverlaps(currival)) {
						// Adjust this range and keep going (this can only possibly happen for one node)
						if (currival.right() > star) {
							star = currival.right();
							starval = current.value();
						}
						currival = new Interval(currival.left(), range.left() - 1);
						current.setInterval(currival);
					} else if (currival.rightOf(range)) {
						path.add(currnode);
						pathIndices.add(i);
						currnode = currnode.subtree(i);
						valid = valid.restrict(previous == null ? Integer.MIN_VALUE : previous.interval().right() + 1, currival.left() - 1);
						continue outerloop;
					}
				}
				path.add(currnode);
				pathIndices.add(numentries);
				currnode = currnode.subtree(numentries);
				valid = valid.restrict(currival == null ? Integer.MIN_VALUE : currival.right() + 1, Integer.MAX_VALUE);
			} while (currnode != null);
		
		if (currnode == null) {
			// In this case, we actually need to do an insertion...
			FigTreeNode rv = null;
			FigTreeEntry topush = new FigTreeEntry(range, value);
			FigTreeNode left = null;
			FigTreeNode right = null;
			FigTreeNode insertinto;
			int insertindex;
			for (int pathindex = path.size() - 1; pathindex >= 0; pathindex--) {
				insertinto = path.get(pathindex);
				insertindex = pathIndices.get(pathindex);
				rv = insertinto.insert(topush, insertindex, left, right);
				if (rv == null) {
					// Nothing to push up
					return;
				}
				topush = rv.entry(0);
				left = rv.subtree(0);
				right = rv.subtree(1);
			}
			
			// No parent to push to
			this.root = rv;
		}
		
		// This recursive call should only happen once, ever
		
		if (star > range.right()) {
			this.insert(new Interval(range.right() + 1, star), starval);
		}
	}
	
	public void insertOld(Interval range, V value) {
		ArrayList<FigTreeNode> path = new ArrayList<FigTreeNode>();
		ArrayList<Integer> pathIndices = new ArrayList<Integer>();
		FigTreeNode currnode = this.root;
		int i;
		
		outerloop:
			do {
				int numentries = currnode.numEntries();
				for (i = 0; i < numentries; i++) {
					FigTreeEntry current = currnode.entry(i);
					Interval currival = current.interval();
					if (currival.left() == range.left()) {
						currnode.replaceEntries(i, i + 1, new FigTreeEntry(range, value));
						return;
					} else if (currival.left() > range.left()) {
						path.add(currnode);
						pathIndices.add(i);
						currnode = currnode.subtree(i);
						continue outerloop;
					}
				}
				path.add(currnode);
				pathIndices.add(numentries);
				currnode = currnode.subtree(numentries);
			} while (currnode != null);
				
		// Now we insert into a leaf and push up
		FigTreeNode rv = null;
		FigTreeEntry topush = new FigTreeEntry(range, value);
		FigTreeNode left = null;
		FigTreeNode right = null;
		FigTreeNode insertinto;
		int insertindex;
		for (int pathindex = path.size() - 1; pathindex >= 0; pathindex--) {
			insertinto = path.get(pathindex);
			insertindex = pathIndices.get(pathindex);
			rv = insertinto.insert(topush, insertindex, left, right);
			if (rv == null) {
				// Nothing to push up
				return;
			}
			topush = rv.entry(0);
			left = rv.subtree(0);
			right = rv.subtree(1);
		}
		
		// No parent to push to
		this.root = rv;
	}
	
	public V lookup(int location) {
		FigTreeNode currnode = this.root;
		
		outerloop:
			do {
				int numentries = currnode.numEntries();
				for (int i = 0; i < numentries; i++) {
					FigTreeEntry current = currnode.entry(i);
					Interval currival = current.interval();
					if (currival.contains(location)) {
						return current.value();
					} else if (currival.left() > location) {
						currnode = currnode.subtree(i);
						continue outerloop;
					}
				}
				currnode = currnode.subtree(numentries);
			} while (currnode != null);
		
		return null;
	}
	
	/* This is used just like a struct. */
	private class FigTreeIterState {
		/*
		 * Stores one node in a path of nodes to reach the current point in the iteration.
		 */
		public FigTreeIterState(FigTreeNode node, Interval valid, FigTreeIterState prev) {
			this.node = node;
			this.entry = null;
			if (node != null) {
				this.entryiter = node.entryIter();
				this.subtreeiter = node.subtreeIter();
			}
			this.valid = valid;
			this.pathprev = prev;
		}
		
		private FigTreeNode node;
		private FigTreeEntry entry;
		private Iterator<FigTreeEntry> entryiter;
		private Iterator<FigTreeNode> subtreeiter;
		private Interval valid;
		private FigTreeIterState pathprev;
	}
	
	public Iterator<V> read(int start, int end) {
		// First, find the start
		Interval initvalid = new Interval(Integer.MIN_VALUE, Integer.MAX_VALUE);
		FigTreeIterState rs = new FigTreeIterState(this.root, initvalid, null);

		outerloop:
			do {
				Interval previval;
				Interval currival = new Interval(Integer.MIN_VALUE, Integer.MIN_VALUE);
				while (rs.entryiter.hasNext()) {
					rs.entry = rs.entryiter.next();
					previval = currival;
					currival = rs.entry.interval();
					if (currival.contains(start)) {
						break outerloop;
					} else if (currival.rightOf(start)) {
						// We won't run into a case where previval and currival are adjacent
						rs = new FigTreeIterState(rs.subtreeiter.next(), rs.valid.restrict(previval.right() + 1, currival.left() - 1), rs);
						continue outerloop;
					}
					rs.subtreeiter.next();
				}
				// So we know what to do when we traverse the subtree and come back here
				rs.entry = null;
				rs = new FigTreeIterState(rs.subtreeiter.next(), rs.valid.restrict(currival.right() + 1, Integer.MAX_VALUE), rs);
			} while (rs.node != null);
		
		if (rs.node == null) {
			/* Didn't find the exact starting byte.
			 * So, stop at the lowest node we got to.
			 */
			rs = rs.pathprev;
		}
		final FigTreeIterState rsfinal = rs;
		
		return new Iterator<V>() {
			public V next() {
				if (this.position >= end) {
					throw new NoSuchElementException();
				}
				V rv = null;
				
				notatend:
					if (rs != null) {
						/* rs === null  when we reach the end of the file; we backtrack past
						 * the root and the readstate becomes null.
						 */
						if (rs.entry.interval().leftOf(position) || rs.valid.leftOf(position)) {
							/* First, descend a subtree until we reach a leaf. */
							
							/* Skip remaining entries if we've moved past the right of the valid interval. */
							if (!rs.entry.interval().rightOf(rs.valid) &&
									!rs.valid.rightOverlaps(rs.entry.interval())) {
								int leftlimit, rightlimit;
								FigTreeNode subtree;
								
								// Mark the entry to come back to after iterating over the subtree
								leftlimit = rs.entry.interval().right() + 1;
								if (rs.entryiter.hasNext()) {
									rs.entry = rs.entryiter.next();
									rightlimit = rs.entry.interval().left() - 1;
								} else {
									rs.entry = null;
									rightlimit = Integer.MAX_VALUE;
								}
								
								subtree = rs.subtreeiter.next();
								
								/* If rs.entry is adjacent to what it used to be, there's no point in traversing this subtree. */
								if (leftlimit <= rightlimit) {
									descendloop:
										while (subtree != null) {
											rs = new FigTreeIterState(subtree, rs.valid.restrict(leftlimit, rightlimit), rs);
											leftlimit = Integer.MIN_VALUE;
											if (rs.entryiter.hasNext()) {
												// So that we know to process it when we come back up here
												rs.entry = rs.entryiter.next();
											}
											
											/* Skip entries to the left of the valid interval. */
											while (rs.entry != null && rs.entry.interval().leftOf(rs.valid)) {
												if (rs.entryiter.hasNext()) {
													rs.entry = rs.entryiter.next();
												} else {
													rs.entry = null;
												}
												rs.subtreeiter.next();
											}
											
											if (rs.entry == null) {
												rightlimit = Integer.MAX_VALUE;
											} else {
												/* If the entry overlaps partially with the interval, then
												 * we can skip the left subtree.
												 */
												if (rs.valid.leftOverlaps(rs.entry.interval())) {
													// Skip iterating over this subtree
													rs.subtreeiter.next();
													break descendloop;
												}
												rightlimit = rs.entry.interval().left();
											}
											subtree = rs.subtreeiter.next();
										}
								}
							}
							
							/* If there was really no subtree (meaning we ended up at the same
							 * node where we started), or the subtree is empty, backtrack up
							 * the tree.
							 */
							while (rs.entry == null) {
								rs = rs.pathprev;
								// If we go past the end of the tree, return null for the unmapped bytes
								if (rs == null) {
									break notatend;
								}
							}
						}
					if (rs.entry.interval().contains(this.position)) {
						rv = this.rs.entry.value();
					}
				}
				this.position++;
				return rv;
			}
			
			public boolean hasNext() {
				return this.position < end;
			}
			
			private int position = start;
			private FigTreeIterState rs = rsfinal;
		};
	}
	
	public String toString() {
		StringBuilder rvbuilder = new StringBuilder();
		
		ArrayList<FigTreeNode> currlevel = new ArrayList<FigTreeNode>();
		ArrayList<FigTreeNode> nextlevel = new ArrayList<FigTreeNode>();
		
		currlevel.add(this.root);
		
		while (currlevel.size() != 0) {
			for (FigTreeNode node : currlevel) {
				rvbuilder.append(node);
				int numentries = node.numEntries();
				for (int i = 0; i <= numentries; i++) {
					/* Due to the B Tree invariants I could move this check outside the loop.
					 * But for debugging, it's useful to know if for some reason the B Tree
					 * isn't balanced.
					 */
					if (node.subtree(i) != null) {
						nextlevel.add(node.subtree(i));
					}
				}
			}
			ArrayList<FigTreeNode> temp = nextlevel;
			nextlevel = currlevel;
			currlevel = temp;
			
			nextlevel.clear();
			
			rvbuilder.append('\n');
		}
		
		return rvbuilder.toString();
	}
	
	private class FigTreeNode {
		public FigTreeNode(int height) {
			if (height < 0) {
				throw new IllegalStateException();
			}
			
			this.HEIGHT = height;
			
			this.clear();
		}
		
		public void clear() {
			this.entries = new ArrayList<FigTreeEntry>();
			this.subtrees = new ArrayList<FigTreeNode>();
			
			FigTreeNode firstchild;
			if (this.HEIGHT == 0) {
				firstchild = null;
			} else {
				firstchild = new FigTreeNode(this.HEIGHT - 1);
			}
			this.subtrees.add(firstchild);
		}
		
		/**
		 * Inserts an entry into this node, and returns the entry to be pushed up to the parent.
		 * @param newent The entry to be inserted.
		 * @param leftChild The new left child of the inserted entry (or null if this is a leaf).
		 * @param rightChild The new right child of the inserted entry (or null if this is a leaf).
		 * @return The entry to be pushed up to the parent, as a node with two children, or null if no node is pushed up.
		 */
		public FigTreeNode insert(FigTreeEntry newent, FigTreeNode leftChild, FigTreeNode rightChild) {
			int index = -Collections.<FigTreeEntry>binarySearch(this.entries, newent);
			if (index < 0) {
				throw new IllegalArgumentException("Insert()ing an interval that already exists");
			}
			return this.insert(newent, index, leftChild, rightChild);
		}
		
		/**
		 * Inserts an entry into this node, and returns the entry to be pushed up to the parent,
		 * given the index at which to insert the entry.
		 * @param index The index at which to insert the entry.
		 * @param leftChild The new left child of the inserted entry (or null if this is a leaf).
		 * @param rightChild The new right child of the inserted entry (or null if this is a leaf).
		 * @return The entry to be pushed up to the parent, as a node with two children, or null if no node is pushed up.
		 */
		public FigTreeNode insert(FigTreeEntry newent, int index, FigTreeNode leftChild, FigTreeNode rightChild) {
			if ((index != 0 && newent.overlaps(this.entries.get(index - 1)))
					|| (index != entries.size() && newent.overlaps(this.entries.get(index)))) {
				throw new IllegalStateException("Insert() violates no-overlap invariant");
			}
			entries.add(index, newent);
			subtrees.set(index, leftChild);
			subtrees.add(index + 1, rightChild);
			if (entries.size() == FigTree.this.SPLITLIMIT) {
				// Split the node and push middle entry to parent
				FigTreeNode left = new FigTreeNode(this.HEIGHT);
				FigTreeNode right = new FigTreeNode(this.HEIGHT);
				Iterator<FigTreeEntry> entryIter = this.entries.iterator();
				Iterator<FigTreeNode> subtreeIter = this.subtrees.iterator();
				int i;
				
				left.subtrees.set(0, subtreeIter.next());
				for (i = 0; i < FigTree.this.ORDER; i++) {
					left.entries.add(entryIter.next());
					left.subtrees.add(subtreeIter.next());
				}
				
				FigTreeEntry middleEntry = entryIter.next();
				right.subtrees.set(0, subtreeIter.next());
				for (i = i + 1; i < FigTree.this.SPLITLIMIT; i++) {
					right.entries.add(entryIter.next());
					right.subtrees.add(subtreeIter.next());
				}
				
				FigTreeNode pushup = new FigTreeNode(this.HEIGHT + 1);
				pushup.entries.add(middleEntry);
				pushup.subtrees.set(0, left);
				pushup.subtrees.add(right);
				return pushup;
			}
			return null;
		}
		
		public void replaceEntries(int start, int end, FigTreeEntry newent) {
			this.entries.set(start, newent);
			this.entries.subList(start + 1, end).clear();
			this.subtrees.subList(start + 1, end).clear();
		}
		
		/**
		 * Prunes this node, given that an interval containing all possible valid entries
		 * (i.e., all entries that are not cached in an ancestor).
		 * @param valid An interval in which all valid entries in this subtree must exist.
		 */
		public void pruneTo(Interval valid) {
			Iterator<FigTreeEntry> entryiter = this.entries.iterator();
			Iterator<FigTreeNode> subtreeiter = this.subtrees.iterator();
			FigTreeEntry entry;
			FigTreeNode subtree;
			Interval entryint = null;
			
			if (this.subtrees.size() != this.entries.size() + 1) {
				throw new IllegalStateException();
			}
			
			// Drop all entries to the left of VALID, along with left subtrees
			if (!entryiter.hasNext()) {
				return;
			}
			
			entryint = (entry = entryiter.next()).interval();
			subtree = subtreeiter.next();
			while (entryint.leftOf(valid)) {
				entryiter.remove();
				subtreeiter.remove();
				
				if (!entryiter.hasNext()) {
					return;
				}
				entryint = (entry = entryiter.next()).interval();
				subtree = subtreeiter.next();
			}
			
			if (valid.leftOverlaps(entryint)) {
				if (subtree != null) {
					subtree.clear();
				}
				
				// In case the valid boundary is in the middle of this interval
				entry.setInterval(entryint.restrict(valid));
				
				if (!entryiter.hasNext()) {
					return;
				}
				entryint = (entry = entryiter.next()).interval();
				subtree = subtreeiter.next();
			}
			
			while (valid.contains(entryint)) {
				if (!entryiter.hasNext()) {
					return;
				}
				entryint = (entry = entryiter.next()).interval();
				subtree = subtreeiter.next();
			}
			
			/* At this point, entryint is either overlapping with the right edge of
			 * VALID, or it is beyond VALID. The left subtree, in either case, cannot
			 * be dropped, since part of it must be in the valid interval. So, just
			 * skip over it. After this, subtreeiter.next() is the right subtree of
			 * entryiter.next().
			 */
			subtree = subtreeiter.next();
			
			if (valid.rightOverlaps(entryint)) {
				if (subtree != null) {
					subtree.clear();
				}
				
				// In case the valid boundary is in the middle of this interval
				entry.setInterval(entryint.restrict(valid));
				
				if (!entryiter.hasNext()) {
					return;
				}
				entryint = (entry = entryiter.next()).interval();
				subtree = subtreeiter.next();
			}
			
			while (entryint.rightOf(valid)) {
				entryiter.remove();
				subtreeiter.remove();
				
				if (!entryiter.hasNext()) {
					return;
				}
				entryint = entryiter.next().interval(); // don't need to assign to entry
				subtreeiter.next(); // don't need to assign to subtree, since henceforth we only need to remove, not clear
			}
		}
		
		/**
		 * Invalidates a left portion of this node, given a cached interval in an ancestor.
		 * @param invalid A cached interval in an ancestor of this node.
		 */
		public void invalidateLeft(Interval invalid) {
			Iterator<FigTreeEntry> entryiter = this.entries.iterator();
			FigTreeEntry e = null;
			Interval entryint = null;
			int i = 0;
			while (entryiter.hasNext()) {
				e = entryiter.next();
				entryint = e.interval();
				if (invalid.contains(entryint)) {
					i++;
				} else {
					break;
				}
			}
			if (e == null) {
				// No entries in this node
				return;
			}
			boolean hasNext = entryiter.hasNext();
			this.entries.subList(0, i).clear();
			if (hasNext && invalid.overlaps(entryint)) {
				entryint = new Interval(invalid.right() + 1, entryint.right());
				e.setInterval(entryint);
				i++;
			}
			this.subtrees.subList(0, i).clear();
		}
		
		/**
		 * Invalidates a right portion of this node, given a cached interval in an ancestor.
		 * @param invalid A cached interval in an ancestor of this node.
		 */
		public void invalidateRight(Interval invalid) {
			int numentries = this.entries.size();
			ListIterator<FigTreeEntry> entryiter = this.entries.listIterator(numentries);
			FigTreeEntry e = null;
			Interval entryint = null;
			int i = numentries;
			while (entryiter.hasPrevious()) {
				e = entryiter.previous();
				entryint = e.interval();
				if (invalid.contains(entryint)) {
					i--;
				} else {
					break;
				}
			}
			if (e == null) {
				// No entries in this node
				return;
			}
			boolean hasPrevious = entryiter.hasPrevious();
			this.entries.subList(i, numentries).clear();
			if (hasPrevious && invalid.overlaps(entryint)) {
				entryint = new Interval(entryint.left(), invalid.left() + 1);
				e.setInterval(entryint);
				i--;
			}
			this.subtrees.subList(i, numentries).clear();
		}
		
		public Iterator<FigTreeEntry> entryIter() {
			return Collections.unmodifiableList(this.entries).iterator();
		}
		
		public Iterator<FigTreeNode> subtreeIter() {
			return Collections.unmodifiableList(this.subtrees).iterator();
		}
		
		public FigTreeEntry entry(int i) {
			return entries.get(i);
		}
		
		public FigTreeNode subtree(int i) {
			return subtrees.get(i);
		}
		
		public int numEntries() {
			if (entries.size() + 1 != subtrees.size()) {
				throw new IllegalStateException();
			}
			return entries.size();
		}
		
		public boolean isLeaf() {
			return this.HEIGHT == 0;
		}
		
		public String toString() {
			StringBuilder rvbuilder = new StringBuilder();
			rvbuilder.append('{');
			for (FigTreeEntry entry : entries) {
				rvbuilder.append(entry);
				rvbuilder.append(',');
			}
			rvbuilder.append('}');
			
			return rvbuilder.toString();
		}
		
		private List<FigTreeEntry> entries;
		private List<FigTreeNode> subtrees;
		private final int HEIGHT;
	}
	
	private class FigTreeEntry implements Comparable<FigTreeEntry> {
		public FigTreeEntry(Interval range, V value) {
			this.irange = range;
			this.value = value;
		}
		
		public void setInterval(Interval newrange) {
			this.irange = newrange;
		}
		
		public Interval interval() {
			return this.irange;
		}
		
		public V value() {
			return value;
		}
		
		public boolean overlaps(FigTreeEntry other) {
			return this.irange.overlaps(other.irange);
		}
		
		public int compareTo(FigTreeEntry other) {
			return other.irange.left() - this.irange.left();
		}
		
		public String toString() {
			return String.format("(%s: %s)", this.irange.toString(), this.value.toString());
		}
		
		private Interval irange;
		private V value;
	}
	
	private FigTreeNode root;
	private final int ORDER;
	private final int SPLITLIMIT;
}
