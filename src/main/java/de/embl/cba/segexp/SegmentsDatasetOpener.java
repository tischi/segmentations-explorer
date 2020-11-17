package de.embl.cba.segexp;

import bdv.viewer.SourceAndConverter;
import de.embl.cba.bdv.utils.Logger;
import de.embl.cba.tables.tablerow.TableRowImageSegment;

import java.io.File;
import java.util.*;

public class SegmentsDatasetOpener implements Runnable
{
	private String rootDirectory;
	private String relativeTablePath;
	private boolean isOneBasedTimePoint;
	private HashMap< SourceAndConverter< ? >, SourceMetadata > sourceToMetadata;
	private List< TableRowImageSegment > segments;

	/**
	 * groupId = imagePathColumnName
	 * imageId = content of the cell of imagePathColumn
	 * isPrimaryLabelSource = ( imagePathColumnName == labelImageColumnName )
	 *
	 * @param rootDirectory
	 * @param relativeTablePath
	 * @param isOneBasedTimePoint
	 */
	public SegmentsDatasetOpener( String rootDirectory, String relativeTablePath, boolean isOneBasedTimePoint )
	{
		this.rootDirectory = rootDirectory;
		this.relativeTablePath = relativeTablePath;
		this.isOneBasedTimePoint = isOneBasedTimePoint;
	}

	@Override
	public void run()
	{
		String tablePath = new File( rootDirectory, relativeTablePath ).toString();

		SegmentsCreator segmentsCreator = new SegmentsCreator( tablePath, isOneBasedTimePoint );
		segments = segmentsCreator.createSegments();
		String labelImageColumnName = segmentsCreator.getLabelImageColumnName();

		ImagePathsFromTableRowsExtractor< TableRowImageSegment > imagePathsExtractor = new ImagePathsFromTableRowsExtractor( segments, "image_path_" );
		Map< String, Set< String > > columnNameToImagePaths = imagePathsExtractor.getColumnNameToImagePaths();

		sourceToMetadata = openSources( rootDirectory, columnNameToImagePaths );
		sourceToMetadata.values().forEach( metadata ->
		{
			metadata.isPrimaryLabelSource = metadata.groupId.contains( labelImageColumnName );
		});
	}

	private static HashMap< SourceAndConverter< ? >, SourceMetadata > openSources( final String rootDirectory, final Map< String, Set< String > > columnNameToImagePaths )
	{
		SourceAndConverterOpener opener = new SourceAndConverterOpener();

		HashMap< SourceAndConverter< ? >, SourceMetadata > sourceToMetadata = new HashMap<>();
		columnNameToImagePaths.keySet().forEach( columnName ->
		{
			Logger.log( "Parsing images from column " + columnName + "..." );
			long currentTimeMillis = System.currentTimeMillis();
			columnNameToImagePaths.get( columnName ).forEach( imagePath ->
			{
				String absolutePath = Utils.createAbsolutePath( rootDirectory, imagePath );
				HashMap< SourceAndConverter< ? >, SourceMetadata > sourcesFromPathToMetadata = opener.open( absolutePath );
				sourcesFromPathToMetadata.keySet().forEach( source ->
				{
					SourceMetadata metadata = sourcesFromPathToMetadata.get( source );
					metadata.imageId = imagePath;
					metadata.groupId = columnName + metadata.channelName;
				} );
				sourceToMetadata.putAll( sourcesFromPathToMetadata );
			});


			Logger.log( "...done in " + ( System.currentTimeMillis() - currentTimeMillis ) + " ms" );
		} );

		return sourceToMetadata;
	}

	public HashMap< SourceAndConverter< ? >, SourceMetadata > getSourceToMetadata()
	{
		return sourceToMetadata;
	}

	public List< TableRowImageSegment > getSegments()
	{
		return segments;
	}

}
