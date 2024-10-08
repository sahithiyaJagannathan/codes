package com.adventnet.zoho.za.rel.model.ml.kmeans;


import com.adventnet.persistence.Row;
import com.adventnet.zoho.za.ChartContext;
import com.adventnet.zoho.za.rel.model.ml.mldbexecutor.GenerateHash;
import com.adventnet.zoho.za.rel.model.ml.mldbexecutor.MLDBExecutor;
import com.zoho.reports.analytics.davinc.aqltovisual.metadata.ZAOnTheFlyFieldInstance;
import com.zoho.reports.analytics.query.model.ZADataProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class KMeans
{

    private String tableName = null;
    private ChartContext ctx = null;
    private ZADataProvider dataProvider;
    private List<Integer> columnIndexList;
    private MLDBExecutor mldbExecutor;
    private boolean isConnectionEstablished;
    private ResultHandler queryResult;
    private KMeansParams params;


    public KMeans( ZADataProvider dataProvider, List<Integer> columnIndexList, String tableName, ChartContext ctx, KMeansParams params ) throws Exception
    {
        this.dataProvider            = dataProvider;
        this.columnIndexList         = columnIndexList;
        this.tableName               = tableName;
        this.mldbExecutor            = new MLDBExecutor();
        this.isConnectionEstablished = this.mldbExecutor.getConnection();
        this.ctx                     = ctx;
        this.params                  = params;
    }

    public void kmeansClustering() throws Exception
    {
        if( this.isConnectionEstablished )
        {

            /*** Create table is created. ***/
            String sqlQuery  = this.getCreateTableQuery();
            this.mldbExecutor.execute( sqlQuery );

            /*** Insert Data  ***/
            sqlQuery  = this.getInsertDataQuery();
            this.mldbExecutor.execute( sqlQuery );
            sqlQuery = "";

            sqlQuery           = this.getKMeansQuery();
            boolean isExecuted = this.mldbExecutor.executeClusterQuery( sqlQuery );

            String resultTablePrefix = null;
            if( isExecuted ){
                String sep           = "_";
                String col           = "";
                int columnCount   = this.columnIndexList.size();
                for ( int i = 1; i <= columnCount; i++ )
                {
                        col += ( "c"+ sep + i+sep );
                }
                String hashText      = this.tableName + sep + col + "kmeans"+( this.params.getClusterCount().equals("NULL")?"None":this.params.getClusterCount() )+"NoneNoneNone";
                GenerateHash hashObj = new GenerateHash( hashText );
                String hash          = hashObj.getHash();
                resultTablePrefix    = "t_"+hash+"_plpy";
            }

            if( resultTablePrefix != null && !resultTablePrefix.equals("") )
            {

                /*** META UPDATE COPIED FROM AJAY CODE ***/
                final ZAOnTheFlyFieldInstance fieldInstance = ZAOnTheFlyFieldInstance.getInstance( this.ctx, ZAOnTheFlyFieldInstance.Type.CLUSTER_TYPE );
                final Row vciRow                            = fieldInstance.getVCIRow();
                dataProvider.addNewZAFieldInstance( new Row[]{ vciRow }, new String[]{ null }, new boolean[]{ true }, this.ctx );
                this.ctx.getZAConfig().addRow(vciRow);
                this.ctx.getDataProvider().getClusterMetaResult().setHasClusters();
                this.ctx.getDataProvider().getClusterMetaResult().setClusterOnTheFlyIndex( dataProvider.getColumnCount() - 1 );
                this.ctx.getDataProvider().getClusterMetaResult().setNecessaryFields( Arrays.stream( this.ctx.getPredictiveInputIndices() ).boxed().collect( Collectors.toList() ), null );

                queryResult = new ResultHandler( resultTablePrefix );

                /*** Copied From AJAY CODE. ***/
                ZADataProvider dataProvider = ctx.getDataProvider();
                int[] clusterIndices        = this.queryResult.getClusterLabels( this.mldbExecutor );
                int clusterColIndex         = dataProvider.getColumnCount() - 1;
                List<Object[]> newDataList  = new ArrayList<>();
                List<Object> dataList       = dataProvider.getDataList();
                for ( int dataProviderIndex = 0; dataProviderIndex < dataProvider.getDataList().size(); dataProviderIndex++ )
                {
                    Object[] datum              = ( Object[] ) dataList.get( dataProviderIndex );
                    Object[] newDatum           = new Object[dataProvider.getColumnCount()];
                    System.arraycopy( datum, 0, newDatum, 0, datum.length );
//                    Integer nullFreeIndex       = this.toNullFreeIndex.apply( dataProviderIndex );
//                    newDatum[ clusterColIndex ] = ( nullList.contains( dataProviderIndex ) ) ?
//                            "Not Clustered" : // No I18N
//                            "Cluster " + ( clusterIndices[nullFreeIndex] + 1 ); // NO I18N
                    newDatum[ clusterColIndex ] = "Cluster "+ ( clusterIndices[ dataProviderIndex ] );
                    newDataList.add( newDatum );
                }
                dataProvider.setDataList( newDataList );
            }
            else{ /*** Need To handle Error ***/ }
        }
    }
    private String getKMeansQuery() throws Exception{
        int columnCount   = this.columnIndexList.size();
        String columnName = "";
        for ( int i = 1; i <= columnCount; i++ )
        {
            if( i < columnCount )
            {
                columnName += ( "c_" + i +"," );
            }
            else
            {
                columnName += "c_"+i;
            }
        }
        String kVlaue = "NULL";
        if( !this.params.getClusterCount().equals("NULL") )
        {
            kVlaue = this.params.getClusterCount();
        }
        String sqlQuery = " SELECT * FROM clustering( '{\"table_name\":\""+ this.tableName+"\"}','{"+columnName+"}','kmeans', "+kVlaue+" )";
        return sqlQuery;
    }

    private String getCreateTableQuery() throws Exception
    {
        String sqlQuery = "CREATE TABLE IF NOT EXISTS "+ this.tableName+" ( ";
        try
        {
            int columnCount = this.columnIndexList.size();
            for ( int i = 1; i <= columnCount; i++ )
            {
                if( i < columnCount )
                {
                    sqlQuery += ( "c_" + i +" DOUBLE PRECISION, " );
                }
                else
                {
                    sqlQuery += "c_"+i+" DOUBLE PRECISION )";
                }
            }
        }
        catch ( Exception error )
        {
            throw new Exception( error );
        }
        return  sqlQuery;
    }

    private String getInsertDataQuery( ) throws Exception
    {
        String sqlQuery     = " INSERT INTO "+ this.tableName + " VALUES ";
        List<Object> data   = this.dataProvider.getDataList();
        try
        {
            for( int rowCount = 0; rowCount < this.dataProvider.getDataList().size(); rowCount++ )
            {
                String values  = " ( ";
                for( int columnListCount = 0; columnListCount < this.columnIndexList.size(); columnListCount++ )
                {
                    int columnIndex = this.columnIndexList.get( columnListCount );
                    String value    = ( ( Object[] ) data.get( rowCount ) )[ columnListCount ].toString();
                    if( columnListCount +1 == this.columnIndexList.size() )
                    {
                        values += ( value +" ) " );
                    }
                    else
                    {
                        values += ( value+"," );
                    }
                }
                if( rowCount +1 ==  this.dataProvider.getDataList().size() )
                {
                    sqlQuery += ( values +";" );
                }else
                {
                    sqlQuery += ( values + "," );
                }
            }
        }
        catch( Exception error )
        {

        }
        return sqlQuery;
    }

    public static class ClusterMetaResult {
        List<Integer> numericFactorIndices = null;
        /* necessary after supporting kModes and kPrototypes */
        // List<Integer> categoricalFactorIndices = null;
        public Integer clusterOnTheFlyIndex = null;
        boolean hasClusters = false;
        boolean disableClustering = false;
        public void disableClustering(boolean disableClustering) {
            this.disableClustering = disableClustering;
        }
        public boolean isClusteringDisabled() {
            return disableClustering;
        }
        public Integer getClusterOnTheFlyIndex() {
            return clusterOnTheFlyIndex;
        }
        public void setClusterOnTheFlyIndex(Integer clusterOnTheFlyIndex) {
            this.clusterOnTheFlyIndex = clusterOnTheFlyIndex;
        }
        public void setNecessaryFields(List<Integer> numericFactorIndices, List<Integer> categoricalFactorIndices) {
            this.numericFactorIndices = numericFactorIndices;
//            this.categoricalFactorIndices = categoricalFactorIndices;
        }
        public boolean hasClusters() {
            return this.hasClusters;
        }
        public void setHasClusters() {
            this.hasClusters = true;
        }
    }
}
