package cz.metacentrum.perun.dispatcher.dao.jdbc;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.transaction.annotation.Transactional;

import cz.metacentrum.perun.dispatcher.dao.DispatcherDao;

@Transactional
public class DispatcherDaoJdbc extends JdbcDaoSupport implements DispatcherDao {

	@Autowired
	private Properties dispatcherProperties;
	private SimpleDateFormat formater = new SimpleDateFormat(
			"yyyyMMdd HH:mm:ss");

	private void cleanUpOldRecords() {
		this.getJdbcTemplate()
				.update("delete from dispatcher_settings where ip_address = ? and port = ?",
						dispatcherProperties.getProperty("dispatcher.ip.address"),
						Integer.parseInt(dispatcherProperties
								.getProperty("dispatcher.port")));
	}

	@Override
	public void registerDispatcher() {
		cleanUpOldRecords();
		this.getJdbcTemplate()
				.update("insert into dispatcher_settings(ip_address, port, last_check_in) values (?,?,to_date(?,'YYYYMMDD HH24:MI:SS'))",
						dispatcherProperties.getProperty("dispatcher.ip.address"),
						Integer.parseInt(dispatcherProperties
								.getProperty("dispatcher.port")),
						formater.format(new Date(System.currentTimeMillis())));
	}


	@Override
	public void checkIn() {
		this.getJdbcTemplate()
				.update("update dispatcher_settings set last_check_in = to_date(?,'YYYYMMDD HH24:MI:SS') where ip_address = ? and port = ?",
						formater.format(new Date(System.currentTimeMillis())),
						dispatcherProperties.getProperty("dispatcher.ip.address"),
						Integer.parseInt(dispatcherProperties
								.getProperty("dispatcher.port")));
	}

	public void setDispatcherProperties(Properties propertiesBean) {
		this.dispatcherProperties = propertiesBean;
	}

	public Properties getDispatcherProperties() {
		return dispatcherProperties;
	}

}
