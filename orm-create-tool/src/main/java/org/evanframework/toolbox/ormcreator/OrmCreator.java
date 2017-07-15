package org.evanframework.toolbox.ormcreator;

import org.apache.commons.lang3.StringUtils;
import org.evanframework.toolbox.ormcreator.database.Database;
import org.evanframework.toolbox.ormcreator.database.DatabaseFactory;
import org.evanframework.toolbox.ormcreator.datatypeconvertor.DataTypeConvertorFactory;
import org.evanframework.toolbox.ormcreator.domain.Column;
import org.evanframework.toolbox.ormcreator.domain.OrmCreatorParam;
import org.evanframework.toolbox.ormcreator.domain.OrmTemplete;
import org.evanframework.toolbox.ormcreator.domain.Table;
import org.evanframework.toolbox.ormcreator.outputor.OrmOutputor;
import org.evanframework.toolbox.ormcreator.utils.OrmCreatorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Orm生成器入口类
 * 
 * @author <a href="mailto:shenw@hundsun.com">Evan.Shen</a>
 * @version Date: 2010-9-11 下午02:48:32
 * @since
 */
public class OrmCreator {
	private static final Logger logger = LoggerFactory.getLogger(OrmCreator.class);

	private OrmCreatorParam param = null;

	/** 数据库读取接口 **/
	private Database database = null;

	/**
	 * 
	 * @param param
	 * @throws Exception
	 */
	public void create(OrmCreatorParam param) {
		this.param = param;

		List<OrmTemplete> ormTempletesForOut = getOrmTempletes(param);

		output(ormTempletesForOut);
	}

	/**
	 * 读取数据库,获取输出对象集合，一个表一个对象
	 * <p>
	 * author: <a href="mailto:shenw@hundsun.com">Evan.Shen</a><br>
	 * version: 2012-1-13 下午12:40:26 <br>
	 * 
	 * @param param
	 */
	private List<OrmTemplete> getOrmTempletes(OrmCreatorParam param) {
		database = DatabaseFactory.getDatabase(param.getDatabaseType(), param);
		database.openConn();
		ResultSet rs = database.getTables(param.getTables());
		// 创建输出对象集合，一个表一个对象
		List<OrmTemplete> ormTempletesForOut = new ArrayList<OrmTemplete>();
		OrmTemplete o = null;
		Table table = null;
		try {
			while (rs.next()) {// 遍历表
				table = database.resultSetToTable(rs);

				o = getOrmTempleteByTable(table);
				ormTempletesForOut.add(o);
			}
			rs.close();
		} catch (SQLException ex) {
			throw new RuntimeException("读取数据库出错", ex);
		}
		database.closeConn();
		return ormTempletesForOut;
	}

	/**
	 * 输出
	 * <p>
	 * author: <a href="mailto:shenw@hundsun.com">Evan.Shen</a><br>
	 * version: 2012-1-13 下午12:40:39 <br>
	 */
	private void output(List<OrmTemplete> ormTempletesForOut) {
		OrmOutputor ormOutputor = new OrmOutputor(param);

		Random random = new Random(4);
		for (OrmTemplete o : ormTempletesForOut) {
			// System.out.println("=================" + outputor.getTableName()
			// + "=================");
			// logger.info("==========================");
			logger.info("table:[" + o.getTableName() + "]");

			o.setSerialVersionUID(System.currentTimeMillis() + random.nextInt());

			ormOutputor.outPub(o);
		}
	}

	/**
	 * 获取每一个表的数据对象，包括表数据和列数据
	 * <p>
	 * author: <a href="mailto:shenw@hundsun.com">Evan.Shen</a><br>
	 * version: 2012-1-13 下午12:51:39 <br>
	 * 
	 * @param table
	 * @throws SQLException
	 */
	private OrmTemplete getOrmTempleteByTable(Table table) throws SQLException {
		// 创建输出对象
		OrmTemplete ormTemplete = new OrmTemplete();

		ormTemplete.setPackageNameDao(param.getPackageNameDao());
		ormTemplete.setPackageNamePo(param.getPackageNamePo());
		ormTemplete.setBasePoName(param.getBasePoName());
		ormTemplete.setBaseDaoName(param.getBaseDaoName());
		ormTemplete.setParam(this.param);

		pubTableDataToOrmTemplete(table.getTableName(), table.getTableComments(), ormTemplete);

		pubColumnsDataToOrmTemplete(table.getTableName(), ormTemplete);

		return ormTemplete;
	}

	/**
	 * 填充一个表中的列对象 到OrmTemplete中
	 * <p>
	 * author: <a href="mailto:shenw@hundsun.com">Evan.Shen</a><br>
	 * version: 2012-1-13 下午1:20:42 <br>
	 * 
	 * @param tableName
	 * @param ormTemplete
	 * @throws SQLException
	 */
	private void pubColumnsDataToOrmTemplete(String tableName, OrmTemplete ormTemplete) throws SQLException {
		List<Column> columns = new ArrayList<Column>();// 所有列
		List<Column> columnNotPKs = new ArrayList<Column>();// 非主键列
		List<Column> columnPKs = new ArrayList<Column>();// 主键列

		// 主键列字符串 如：单主键 "ID",多主键："CODE,TYPE"
		StringBuilder pkColumnNames = new StringBuilder();
		// 主键对象字符串 如：单主键 "id" ,多主键："code,type"
		StringBuilder pkFieldNames = new StringBuilder();
		// 主键参数表达式 如：单主键 "Long id",多主键："String code,Sting type"
		StringBuilder pkParams = new StringBuilder();
		// StringBuilder pkWhere = new StringBuilder();//主键where字句
		String pkIbatisDataType = null;
		String pkJavaDataType = null;
		//boolean isPk = false;
		// pkColumnNames.delete(0, pkColumnNames.length());
		// pkParams.delete(0, pkParams.length());
		// pkWhere.delete(0, pkWhere.length());

		// 类型转换器
		if (param.getDataTypeConvertor() == null) {
			// 如果没有提供，就用默认的类型转换器
			param.setDataTypeConvertor(DataTypeConvertorFactory.getDataTypeConvertor(param.getDatabaseType()));
		}

		Column column = null;

		// 列表，java数据类型，java名，java名首字母大写
		String columnName, javaDataType, javaFieldName, javaFieldNameFirstCharUpcase, columnKey;

		ResultSet rs = database.getColumns(tableName);
		while (rs.next()) {
			column = database.resultSetToColumn(rs);

			javaFieldNameFirstCharUpcase = OrmCreatorUtil.convertDBNameToJavaName(column.getColumnName());
			column.setFieldNameFirstCharUpcase(javaFieldNameFirstCharUpcase);// java名首字母大写
			javaFieldName = OrmCreatorUtil.toFirstCharLow(javaFieldNameFirstCharUpcase);
			column.setFieldName(javaFieldName);// java名名首字母小写

			columns.add(column);

			if (column.getIsPk() == 1) {
				pkColumnNames.append("," + column.getColumnName());// 合并主键列的字段名
				pkFieldNames.append("," + column.getFieldName());// 合并主键列得java名
				pkParams.append("," + column.getDataType() + " " + column.getFieldName());// 合并主键参数
				pkIbatisDataType = OrmCreatorUtil.convertJavaDataTypeToSimple(column.getDataType());
				pkJavaDataType = column.getDataType();
				columnPKs.add(column);
			} else {
				columnNotPKs.add(column);
			}
		}

		if (pkColumnNames.length() > 0) { // 去掉第一个“,”
			pkColumnNames.deleteCharAt(0);
			pkParams.deleteCharAt(0);
			pkFieldNames.deleteCharAt(0);
		}

		ormTemplete.setColumns(columns);
		ormTemplete.setColumnNotPKs(columnNotPKs);
		ormTemplete.setColumnPKs(columnPKs);
		ormTemplete.setPkCount(columnPKs.size());
		ormTemplete.setPkColumnNames(pkColumnNames.toString());
		ormTemplete.setPkParams(pkParams.toString());
		ormTemplete.setPkFieldNames(pkFieldNames.toString());
		ormTemplete.setPkIbatisDataType(pkIbatisDataType);
		ormTemplete.setPkJavaDataType(pkJavaDataType);
	}

	/**
	 * 填充一个表的表对象 到OrmTemplete
	 * <p>
	 * author: <a href="mailto:shenw@hundsun.com">Evan.Shen</a><br>
	 * version: 2012-1-13 下午1:21:15 <br>
	 * 
	 * @param tableName
	 * @param tableComment
	 * @param ormTemplete
	 */
	private void pubTableDataToOrmTemplete(String tableName, String tableComment, OrmTemplete ormTemplete) {
		String className = OrmCreatorUtil.convertDBNameToJavaName(tableName);// 表名装换成类名
		String prefixRemove = this.param.getPrefixRemove();
		if (StringUtils.isNotBlank(prefixRemove)) { // 是否去掉前缀
			int length = prefixRemove.length();
			String classNamePrefix = className.substring(0, length);
			if (StringUtils.endsWithIgnoreCase(classNamePrefix, prefixRemove)) {
				className = className.substring(length);
			}
		}

		String objectName = OrmCreatorUtil.toFirstCharLow(className);// 类名装换对象名，即将首字母改为小写

		ormTemplete.setTableName(tableName);
		ormTemplete.setClassName(className);
		ormTemplete.setObjectName(objectName);
		ormTemplete.setComment(tableComment); // 表注释
		ormTemplete.setTableAlias(OrmCreatorUtil.convertTableNameToTableAlias(tableName));

	}
}
