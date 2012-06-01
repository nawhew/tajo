/**
 * 
 */
package nta.engine.planner.global;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;

import nta.catalog.Schema;
import nta.catalog.statistics.TableStat;
import nta.engine.AbstractQuery;
import nta.engine.MasterInterfaceProtos.Partition;
import nta.engine.QueryUnitId;
import nta.engine.ipc.protocolrecords.Fragment;
import nta.engine.planner.logical.BinaryNode;
import nta.engine.planner.logical.ExprType;
import nta.engine.planner.logical.LogicalNode;
import nta.engine.planner.logical.ScanNode;
import nta.engine.planner.logical.StoreTableNode;
import nta.engine.planner.logical.UnaryNode;

import com.google.common.base.Preconditions;
import org.apache.hadoop.thirdparty.guava.common.collect.Sets;

/**
 * @author jihoon
 *
 */
public class QueryUnit extends AbstractQuery {
  
  private final static int EXPIRE_TIME = 5000;

	private QueryUnitId id;
	private StoreTableNode store = null;
	private LogicalNode plan = null;
	private List<ScanNode> scan;
	
	private String hostName;
	private Map<String, List<Fragment>> fragMap;
	private Map<String, Set<URI>> fetchMap;
	
	private int expire;
	private List<Partition> partitions;
	private TableStat stats;
	
	public QueryUnit(QueryUnitId id) {
		this.id = id;
		scan = new ArrayList<ScanNode>();
    fetchMap = new HashMap<String, Set<URI>>();
    fragMap = new HashMap<String, List<Fragment>>();
    partitions = new ArrayList<Partition>();
    expire = QueryUnit.EXPIRE_TIME;
	}
	
	public void setLogicalPlan(LogicalNode plan) {
    Preconditions.checkArgument(plan.getType() == ExprType.STORE);
    
	  this.plan = plan;
	  store = (StoreTableNode) plan;
	  LogicalNode node = plan;
	  ArrayList<LogicalNode> s = new ArrayList<LogicalNode>();
	  s.add(node);
	  while (!s.isEmpty()) {
	    node = s.remove(s.size()-1);
	    if (node instanceof UnaryNode) {
	      UnaryNode unary = (UnaryNode) node;
	      s.add(s.size(), unary.getSubNode());
	    } else if (node instanceof BinaryNode) {
	      BinaryNode binary = (BinaryNode) node;
	      s.add(s.size(), binary.getOuterNode());
	      s.add(s.size(), binary.getInnerNode());
	    } else if (node instanceof ScanNode) {
	      scan.add((ScanNode)node);
	    }
	  }
	}
	
	public void setHost(String host) {
		this.hostName = host;
	}
	
	public void addFragment(String key, Fragment fragment) {
	  List<Fragment> frags;
	  if (fragMap.containsKey(key)) {
	    frags = fragMap.get(key);
	  } else {
	    frags = new ArrayList<Fragment>();
	  }
	  frags.add(fragment);
		this.fragMap.put(key, frags);
	}
	
	public void addFragments(String key, Fragment[] fragments) {
	  for (Fragment frag : fragments) {
      this.addFragment(key, frag);
    }
	}
	
	public void addFragments(String key, List<Fragment> fragList) {
	  for (Fragment frag : fragList) {
	    this.addFragment(key, frag);
	  }
	}
	
	public void addFetch(String key, String uri) throws URISyntaxException {
	  this.addFetch(key, new URI(uri));
	}
	
	public void addFetch(String key, URI uri) {
	  Set<URI> uris;
	  if (fetchMap.containsKey(key)) {
	    uris = fetchMap.get(key);
	  } else {
	    uris = Sets.newHashSet();
	  }
	  uris.add(uri);
    fetchMap.put(key, uris);
	}
	
	public void addFetches(String key, List<URI> urilist) {
	  Set<URI> uris;
    if (fetchMap.containsKey(key)) {
      uris = fetchMap.get(key);
    } else {
      uris = Sets.newHashSet();
    }
    uris.addAll(urilist);
    fetchMap.put(key, uris);
	}
	
	public void setFetches(Map<String, Set<URI>> fetches) {
	  this.fetchMap.clear();
	  this.fetchMap.putAll(fetches);
	}
	
	public List<Fragment> getFragments(String key) {
		return this.fragMap.get(key);
	}
	
	public LogicalNode getLogicalPlan() {
	  return this.plan;
	}
	
	public QueryUnitId getId() {
		return id;
	}
	
	public Collection<URI> getFetchHosts(String tableId) {
	  return fetchMap.get(tableId);
	}
	
	public Collection<Set<URI>> getFetches() {
	  return fetchMap.values();
	}
	
	public Collection<URI> getFetch(ScanNode scan) {
	  return this.fetchMap.get(scan.getTableId());
	}
	
	public String getHost() {
		return this.hostName;
	}
	
	public String getOutputName() {
		return this.store.getTableName();
	}
	
	public Schema getOutputSchema() {
	  return this.store.getOutputSchema();
	}
	
	public StoreTableNode getStoreTableNode() {
	  return this.store;
	}
	
	public ScanNode[] getScanNodes() {
	  return this.scan.toArray(new ScanNode[scan.size()]);
	}
	
	@Override
	public String toString() {
		String str = new String(plan.getType() + " ");
		for (Entry<String, List<Fragment>> e : fragMap.entrySet()) {
		  str += e.getKey() + " : ";
		  for (Fragment t : e.getValue()) {
	      str += t + " ";
	    }
		}
		for (Entry<String, Set<URI>> e : fetchMap.entrySet()) {
      str += e.getKey() + " : ";
      for (URI t : e.getValue()) {
        str += t + " ";
      }
    }
		
		return str;
	}
	
	public void setStats(TableStat stats) {
	  this.stats = stats;
	}
	
	public void addPartitions(List<Partition> partitions) {
	  this.partitions.addAll(partitions);
	}
	
	public void addPartition(Partition partition) {
	  this.partitions.add(partition);
	}
	
	public TableStat getStats() {
	  return this.stats;
	}
	
	public List<Partition> getPartitions() {
	  return this.partitions;
	}
	
	public int getPartitionNum() {
	  return this.partitions.size();
	}
	
	/*
	 * Expire time
	 */
	
	public void setExpireTime(int expire) {
	  this.expire = expire;
	}
	
	public void updateExpireTime(int period) {
	  this.setExpireTime(this.expire - period);
	}
	
	public void resetExpireTime() {
	  this.setExpireTime(QueryUnit.EXPIRE_TIME);
	}
	
	public int getLeftTime() {
	  return this.expire;
	}
}
