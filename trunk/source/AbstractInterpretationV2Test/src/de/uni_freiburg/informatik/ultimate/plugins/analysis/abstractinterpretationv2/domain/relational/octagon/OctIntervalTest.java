package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.relational.octagon;

import org.junit.Assert;
import org.junit.Test;

public class OctIntervalTest {

	@Test
	public void testTopBottom() {
		OctInterval top = new OctInterval(OctValue.parse("inf"), OctValue.INFINITY);
		OctInterval openRight = new OctInterval(OctValue.ZERO, OctValue.INFINITY);
		OctInterval openLeft = new OctInterval(OctValue.INFINITY, OctValue.ZERO);
		OctInterval point = new OctInterval(OctValue.parse("2.1"), OctValue.parse("2.10"));
		OctInterval bot = new OctInterval(OctValue.parse("-1"), OctValue.parse("-1.1"));
		
		Assert.assertFalse(top.isBottom());
		Assert.assertTrue(top.isTop());
		Assert.assertFalse(openRight.isBottom());
		Assert.assertFalse(openRight.isTop());
		Assert.assertFalse(openLeft.isBottom());
		Assert.assertFalse(openLeft.isTop());
		Assert.assertFalse(point.isBottom());
		Assert.assertFalse(point.isTop());
		Assert.assertTrue(bot.isBottom());
		Assert.assertFalse(bot.isTop());
	}
	
}