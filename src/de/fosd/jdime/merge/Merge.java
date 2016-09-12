/**
 * Copyright (C) 2013-2014 Olaf Lessenich
 * Copyright (C) 2014-2015 University of Passau, Germany
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 *
 * Contributors:
 *     Olaf Lessenich <lessenic@fim.uni-passau.de>
 *     Georg Seibt <seibt@fim.uni-passau.de>
 */
package de.fosd.jdime.merge;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.fosd.jdime.common.ASTNodeArtifact;
import de.fosd.jdime.common.Artifact;
import de.fosd.jdime.common.MergeContext;
import de.fosd.jdime.common.MergeScenario;
import de.fosd.jdime.common.Revision;
import de.fosd.jdime.common.operations.ConflictOperation;
import de.fosd.jdime.common.operations.DeleteOperation;
import de.fosd.jdime.common.operations.MergeOperation;
import de.fosd.jdime.matcher.Matcher;
import de.fosd.jdime.matcher.matching.Color;
import de.fosd.jdime.matcher.matching.Matching;

import static de.fosd.jdime.strdump.DumpMode.PLAINTEXT_TREE;

/**
 * @author Olaf Lessenich
 *
 * @param <T>
 *            type of artifact
 */
public class Merge<T extends Artifact<T>> implements MergeInterface<T> {

    private static final Logger LOG = Logger.getLogger(Merge.class.getCanonicalName());

    private UnorderedMerge<T> unorderedMerge = null;
    private OrderedMerge<T> orderedMerge = null;
    private String logprefix;

    /**
     * TODO: this needs high-level explanation.
     *
     * @param operation the <code>MergeOperation</code> to perform
     * @param context the <code>MergeContext</code>
     */
    @Override
    public void merge(MergeOperation<T> operation, MergeContext context) {
        logprefix = operation.getId() + " - ";
        MergeScenario<T> triple = operation.getMergeScenario();
        T left = triple.getLeft();
        T base = triple.getBase();
        T right = triple.getRight();
        T target = operation.getTarget();

        Revision l = left.getRevision();
        Revision b = base.getRevision();
        Revision r = right.getRevision();

        if (!context.isDiffOnly() && !context.isPretend()) {
            Objects.requireNonNull(target, "target must not be null!");
        }

        Matcher<T> matcher = new Matcher<>();
        Matching<T> m;

        if (!left.hasMatching(r) && !right.hasMatching(l)) {
            if (!base.isEmpty()) {
                // 3-way merge

                // diff base left
                m = matcher.match(context, base, left, Color.GREEN).get(base, left).get();

                if (m.getScore() == 0) {
                    LOG.fine(() -> String.format("%s and %s have no matches.", base.getId(), left.getId()));
                }

                // diff base right
                m = matcher.match(context, base, right, Color.GREEN).get(base, right).get();

                if (m.getScore() == 0) {
                    LOG.fine(() -> String.format("%s and %s have no matches.", base.getId(), right.getId()));
                }
            }

            // diff left right
            m = matcher.match(context, left, right, Color.BLUE).get(left, right).get();

            if (context.isDiffOnly() && left.isRoot() && left instanceof ASTNodeArtifact) {
                assert (right.isRoot());
                return;
            }

            if (m.getScore() == 0) {
                LOG.fine(() -> String.format("%s and %s have no matches.", left.getId(), right.getId()));
                return;
            }
        }
        
        if (context.isDiffOnly() && left.isRoot()) {
            assert (right.isRoot());
            return;
        }

        if (!((left.isChoice() || left.hasMatching(right)) && right.hasMatching(left))) {
            LOG.severe(left.getId() + " and " + right.getId() + " have no matches.");
            LOG.severe("left: " + left.findRoot().dump(PLAINTEXT_TREE));
            LOG.severe("right: " + right.findRoot().dump(PLAINTEXT_TREE));
            throw new RuntimeException();
        }

        if (target != null && target.isRoot() && !target.hasMatches()) {
            // hack to fix the matches for the merged root node
            target.cloneMatches(left);
        }

        // check if one or both the nodes have no children
        List<T> leftChildren = left.getChildren();
        List<T> rightChildren = right.getChildren();

        LOG.finest(() -> String.format("%s Children that need to be merged:", prefix()));
        LOG.finest(() -> String.format("%s -> (%s)", prefix(left), leftChildren));
        LOG.finest(() -> String.format("%s -> (%s)", prefix(right), rightChildren));

        if ((base.isEmpty() || base.hasChildren()) && (leftChildren.isEmpty() || rightChildren.isEmpty())) {
            if (leftChildren.isEmpty() && rightChildren.isEmpty()) {
                LOG.finest(() -> String.format("%s and [%s] have no children", prefix(left), right.getId()));
                return;
            } else if (leftChildren.isEmpty()) {
                LOG.finest(() -> String.format("%s has no children", prefix(left)));
                LOG.finest(() -> String.format("%s was deleted by left", prefix(right)));

                if (right.hasChanges(b)) {
                    LOG.finest(() -> String.format("%s has changes in subtree", prefix(right)));

                    for (T rightChild : right.getChildren()) {
                        ConflictOperation<T> conflictOp = new ConflictOperation<>(
                                null, rightChild, target, l.getName(), r.getName());
                        conflictOp.apply(context);
                    }
                    return;
                } else {

                    for (T rightChild : rightChildren) {

                        DeleteOperation<T> delOp = new DeleteOperation<>(rightChild, target, triple, l.getName());
                        delOp.apply(context);
                    }
                    return;
                }
            } else if (rightChildren.isEmpty()) {
                LOG.finest(() -> String.format("%s has no children", prefix(right)));
                LOG.finest(() -> String.format("%s was deleted by right", prefix(left)));

                if (left.hasChanges(b)) {
                    LOG.finest(() -> String.format("%s has changes in subtree", prefix(left)));

                    for (T leftChild : left.getChildren()) {
                        ConflictOperation<T> conflictOp = new ConflictOperation<>(
                                leftChild, null, target, l.getName(), r.getName());
                        conflictOp.apply(context);
                    }
                    return;
                } else {

                    for (T leftChild : leftChildren) {
                        DeleteOperation<T> delOp = new DeleteOperation<>(leftChild, target, triple, r.getName());
                        delOp.apply(context);
                    }
                    return;
                }
            } else {
                throw new RuntimeException("Something is very broken.");
            }
        }

        // determine whether we have to respect the order of children
        boolean isOrdered = false;
        for (int i = 0; !isOrdered && i < left.getNumChildren(); i++) {
            if (left.getChild(i).isOrdered()) {
                isOrdered = true;
            }
        }
        for (int i = 0; !isOrdered && i < right.getNumChildren(); i++) {
            if (right.getChild(i).isOrdered()) {
                isOrdered = true;
            }
        }

        if (LOG.isLoggable(Level.FINEST) && target != null) {
            LOG.finest(String.format("%s target.dumpTree() before merge:", logprefix));
            System.out.println(target.findRoot().dump(PLAINTEXT_TREE));
        }

        if (isOrdered) {
            if (orderedMerge == null) {
                orderedMerge = new OrderedMerge<>();
            }
            orderedMerge.merge(operation, context);
        } else {
            if (unorderedMerge == null) {
                unorderedMerge = new UnorderedMerge<>();
            }
            unorderedMerge.merge(operation, context);
        }
    }

    /**
     * Returns the logging prefix.
     *
     * @return logging prefix
     */
    private String prefix() {
        return logprefix;
    }

    /**
     * Returns the logging prefix.
     *
     * @param artifact
     *            artifact that is subject of the logging
     * @return logging prefix
     */
    private String prefix(T artifact) {
        return String.format("%s[%s]", logprefix, (artifact == null) ? "null" : artifact.getId());
    }
}
