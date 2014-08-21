package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.Collection;

import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootAnnot;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.benchmark.BenchmarkData;
import de.uni_freiburg.informatik.ultimate.util.csv.ICsvProvider;
import de.uni_freiburg.informatik.ultimate.util.csv.ICsvProviderProvider;
import de.uni_freiburg.informatik.ultimate.util.csv.SimpleCsvProvider;

public class TraceAbstractionBenchmarks implements ICsvProviderProvider<Object>{
	
	private final BenchmarkData m_CegarLoopBenchmarkData;
	private final int m_Locations;
	private final int m_ErrorLocations;
	
	public TraceAbstractionBenchmarks(RootAnnot rootAnnot) {
		m_Locations = rootAnnot.getNumberOfProgramPoints();
		m_ErrorLocations = rootAnnot.getNumberOfErrorNodes();
		m_CegarLoopBenchmarkData = new BenchmarkData();
	}
	
	public void aggregateBenchmarkData(
			CegarLoopBenchmarkGenerator cegarLoopBenchmarkGenerator) {
		m_CegarLoopBenchmarkData.aggregateBenchmarkData(cegarLoopBenchmarkGenerator);
	}

	public static String prettyprintNanoseconds(long time) {
		long seconds = time/1000000000;
		long tenthDigit = (time/100000000) % 10;
		return seconds + "." + tenthDigit + "s";
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("CFG has ");
		sb.append(m_Locations);
		sb.append(" locations, ");
		sb.append(m_ErrorLocations);
		sb.append(" error locations. ");
		sb.append(m_CegarLoopBenchmarkData.toString());
		return sb.toString();
	}

	@Override
	public ICsvProvider<Object> createCvsProvider() {
		Collection<String> keys = m_CegarLoopBenchmarkData.getKeys();
		String[] keysArray = keys.toArray(new String[keys.size()]);
		SimpleCsvProvider<Object> scp = new SimpleCsvProvider<Object>(keysArray);
		
		Object[] values = new Object[keys.size()];
		int offset = 0;
		for (String key : keys) {
			values[offset] = m_CegarLoopBenchmarkData.getValue(key);
			offset++;
		}
		scp.addRow(this.getClass().getName(), values );
		return scp;
	}
	
}
