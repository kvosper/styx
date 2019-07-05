package com.hotels.styx.metrics;

import com.hotels.styx.common.Pair;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static com.hotels.styx.common.Pair.pair;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

public class SimpleTreeTest {
    private SimpleTree<Object> tree;

    @BeforeMethod
    public void setUp() {
        tree = new SimpleTree<>();
    }

    @Test
    public void matchesNodesAdded() {
        tree.add("foo.bar.baz", 1);
        tree.add("alice.bob", 2);

        assertThat(tree.exists("foo.bar.baz"), is(true));
        assertThat(tree.exists("alice.bob"), is(true));
        assertThat(tree.exists("this.does.not.exist"), is(false));

        assertThat(tree.valueOf("foo.bar.baz"), isValue(1));
        assertThat(tree.valueOf("alice.bob"), isValue(2));
        assertThat(tree.valueOf("this.does.not.exist"), isAbsent());
    }

    @Test
    public void doesNotMatchIncompleteNames() {
        tree.add("foo.bar.baz", 1);
        tree.add("alice.bob", 1);

        assertThat(tree.exists("foo.bar"), is(false));
        assertThat(tree.exists("alice"), is(false));

        assertThat(tree.valueOf("foo.bar"), isAbsent());
        assertThat(tree.valueOf("alice"), isAbsent());
    }

    @Test
    public void matchesNodesWithWildcards() {
        tree.add("foo.*.baz", 1);

        assertThat(tree.valueOf("foo.bar.baz"), isValue(1));
        assertThat(tree.valueOf("foo.alice.baz"), isValue(1));
    }

    @Test
    public void canAddNamesThatMatchPartOfExistingNames() {
        tree.add("foo.bar.baz", 1);
        tree.add("foo.bar", 2);

        assertThat(tree.exists("foo.bar.baz"), is(true));
        assertThat(tree.exists("foo.bar"), is(true));

        assertThat(tree.valueOf("foo.bar.baz"), isValue(1));
        assertThat(tree.valueOf("foo.bar"), isValue(2));
    }

    @Test
    public void canAddNamesThatStartWithExistingNames() {
        tree.add("foo.bar", 1);
        tree.add("foo.bar.baz", 2);

        assertThat(tree.exists("foo.bar"), is(true));
        assertThat(tree.exists("foo.bar.baz"), is(true));

        assertThat(tree.valueOf("foo.bar"), isValue(1));
        assertThat(tree.valueOf("foo.bar.baz"), isValue(2));
    }

    @Test
    public void walkOutputsEntries() {
        tree.add("foo.bar.baz", 1);
        tree.add("foo.x.y", 4);
        tree.add("alice.bob", 2);
        tree.add("alice.charlie", 3);

        List<Pair<String, Integer>> items = new ArrayList<>();
        tree.walk((name, value) -> items.add(pair(name, (Integer) value)));

        assertThat(items, containsInAnyOrder(
                pair("foo.bar.baz", 1),
                pair("foo.x.y", 4),
                pair("alice.bob", 2),
                pair("alice.charlie", 3)
        ));
    }

    @Test
    public void canWalkNonWildcardEntriesOnly() {
        tree.add("foo.bar.baz", 1);
        tree.add("foo.x.*", 4);
        tree.add("alice.bob", 2);
        tree.add("alice.charlie", 3);

        List<Pair<String, Integer>> items = new ArrayList<>();
        tree.walkNonWildcards((name, value) -> items.add(pair(name, (Integer) value)));

        assertThat(items, containsInAnyOrder(
                pair("foo.bar.baz", 1),
                pair("alice.bob", 2),
                pair("alice.charlie", 3)
        ));
    }
}