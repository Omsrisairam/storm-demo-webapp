package poc.hortonworks.storm.demoreset.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import poc.hortonworks.storm.config.service.AppConfigService;
import poc.hortonworks.storm.demoreset.web.DemoResetParam;
import poc.hortonworks.storm.streamgenerator.service.StreamGeneratorService;

@Service
public class DemoResetService {
	

	private static Logger LOG = Logger.getLogger(DemoResetService.class);
	
	private static final  String EVENTS_TABLE_NAME = "driver_dangerous_events";
	private static final  String EVENTS_COUNT_TABLE_NAME = "driver_dangerous_events_count";
	
	private HBaseAdmin admin;
	private HTable driverEventsTable;
	private HTable driverEventsCountTable;

	private Connection phoenixConnection;
	
	private StreamGeneratorService streamService;
	private AppConfigService appConfigService;
	
	@Autowired
	public DemoResetService(StreamGeneratorService streamService, AppConfigService appConfigService) {
		try {
			this.streamService = streamService;
			this.appConfigService = appConfigService;
			
			Configuration config = constructConfiguration();
			admin = createHBaseAdmin(config);
			HConnection connection = HConnectionManager.createConnection(config);
			driverEventsTable = (HTable) connection.getTable(EVENTS_TABLE_NAME);
			driverEventsCountTable = (HTable) connection.getTable(EVENTS_COUNT_TABLE_NAME);
			
			this.phoenixConnection = DriverManager.getConnection(appConfigService.getPhoenixConnectionURL());
			this.phoenixConnection.setAutoCommit(true);				

		} catch (Exception e) {
			LOG.error("Error connectiong to HBase", e);
			throw new RuntimeException("Error Connecting to HBase", e);
		}	
	}
	
	public void resetDemo(DemoResetParam param) {
		if(param.isTruncateHbaseTables()) {
			truncateHBaseTables();
			truncatePhoenixTables();
		}
		//resetStreamingSimulator();
		streamService.resetMapCords();
	}

	
	private void truncatePhoenixTables() {
		
		PreparedStatement statement = null;
		try {
			statement = phoenixConnection.prepareStatement("delete from  truck.dangerous_events");
			statement.execute();
			
		} catch (SQLException e) {
			LOG.error("Error truncating Phoenexing tables");
		} finally {
			if(statement != null) {
				try {
					statement.close();
				} catch (SQLException e) {
					LOG.error("Error closing connection", e);
				}
			}
		}
	}

	public void truncateHBaseTables() {
		try {
			
			truncateTable(driverEventsTable);
			truncateTable(driverEventsCountTable);
		} catch (Exception  e) {
			LOG.error("Error truncating HBase tables", e);
			//do nothing
		}			
	}
	
	private void truncateTable(HTable table)
			throws IOException, TableNotFoundException {	
		
		HTableDescriptor tableDescriptor = table.getTableDescriptor();
		TableName tableName = table.getName();
		admin.disableTable(tableName);
		admin.deleteTable(tableName);
		admin.createTable(tableDescriptor, table.getStartKeys());
	}
	
	private HBaseAdmin createHBaseAdmin(Configuration config) throws Exception {
		HBaseAdmin admin = new HBaseAdmin(config);
		return admin;
	}


	private Configuration constructConfiguration() throws Exception {
		Configuration config = HBaseConfiguration.create();
		config.set("hbase.zookeeper.quorum",
				appConfigService.getHBaseZookeeperHost());
		config.set("hbase.zookeeper.property.clientPort", appConfigService.getHBaseZookeeperClientPort());
		config.set("zookeeper.znode.parent", appConfigService.getHBaseZookeeperZNodeParent());
		return config;
	}	
}
