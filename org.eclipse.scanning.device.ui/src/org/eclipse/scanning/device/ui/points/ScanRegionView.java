package org.eclipse.scanning.device.ui.points;

import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.preferences.BasePlottingConstants;
import org.eclipse.dawnsci.plotting.api.region.ColorConstants;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.IRegionListener;
import org.eclipse.dawnsci.plotting.api.region.RegionEvent;
import org.eclipse.dawnsci.plotting.api.region.RegionUtils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.richbeans.widgets.internal.GridUtils;
import org.eclipse.richbeans.widgets.menu.MenuAction;
import org.eclipse.scanning.api.points.models.ScanRegion;
import org.eclipse.scanning.device.ui.Activator;
import org.eclipse.scanning.device.ui.DevicePreferenceConstants;
import org.eclipse.scanning.device.ui.ServiceHolder;
import org.eclipse.scanning.device.ui.util.PlotUtil;
import org.eclipse.scanning.device.ui.util.Stashing;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * A tool for editing scan regions. The view creates/edits regions
 * and then encapsulates then into a ScanRegion object which the axes
 * upon which they act.
 * 
 * It translates the IROI's created in data coordinates to IROI's in
 * axis coordinates and can be used via getAdpter(...) to return a list
 * of these ScanRegions which may be packaged in the CompoundModel which
 * is given to the server.
 * 
 * TODO This view is only linked to one plotting system which is 
 * read when the view is created. Should the view actually be a 
 * static tool which updates the scan regions for any plotting system?
 * Or should there be an action to change plotting systems that
 * the view is linked to?
 * 
 * @author Matthew Gerring
 *
 */
public class ScanRegionView extends ViewPart {
	
	public static final String ID = "org.eclipse.scanning.device.ui.points.scanRegionView";
	
	private static Logger logger = LoggerFactory.getLogger(ScanRegionView.class);

	private static final Collection<RegionType> regionTypes;
	static {
		regionTypes = new ArrayList<RegionType>(6);
		regionTypes.add(RegionType.BOX);
		regionTypes.add(RegionType.POLYGON);
		regionTypes.add(RegionType.RING);
		regionTypes.add(RegionType.SECTOR);
		regionTypes.add(RegionType.ELLIPSE);
	}

	// UI
	private TableViewer        viewer;
	private IPlottingSystem<?> system;

	// Listeners
	private IRegionListener regionListener;
	
	// File
	private Stashing stash;
	
	public ScanRegionView() {
		
		Activator.getDefault().getPreferenceStore().setDefault(DevicePreferenceConstants.AUTO_SAVE_REGIONS, true);
		this.stash = new Stashing("org.eclipse.scanning.device.ui.scan.regions.json", ServiceHolder.getEventConnectorService());
		
		this.regionListener = new IRegionListener.Stub() {

			protected void update(RegionEvent evt) {
				viewer.refresh();
			}
		};
	}

	@Override
	public void createPartControl(Composite ancestor) {
		
		// TODO Action to choose a different plotting system?
		this.system = getPlottingSystem();

		Composite control = new Composite(ancestor, SWT.NONE);
		control.setLayout(new GridLayout(1, false));
		GridUtils.removeMargins(control);
		
		this.viewer = new TableViewer(control, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
		viewer.setContentProvider(new ScanRegionProvider());
		
		viewer.getTable().setLinesVisible(true);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
		// resize the row height using a MeasureItem listener
		viewer.getTable().addListener(SWT.MeasureItem, new Listener() {
	        public void handleEvent(Event event) {
	            event.height = 24;
	        }
	    });

	    //added 'event.height=rowHeight' here just to check if it will draw as I want
		viewer.getTable().addListener(SWT.EraseItem, new Listener() {
	        public void handleEvent(Event event) {
	            event.height=24;
	        }
	    });		
		
		viewer.getTable().addKeyListener(new KeyListener() {
			public void keyReleased(KeyEvent e) {
			}

			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.F1) {
					// TODO Help!
				}
				if (e.character == SWT.DEL) {
					try {
						Object ob = ((IStructuredSelection)viewer.getSelection()).getFirstElement();
						ScanRegion<IROI> region = (ScanRegion<IROI>)ob;
						IRegion sregion = system.getRegion(region.getName());
						if (sregion!=null) system.removeRegion(sregion);
						viewer.refresh(); // Must do global refresh because might effect units of other parameters.
					} catch (Exception ne) {
						logger.error("Cannot delete item "+(IStructuredSelection)viewer.getSelection(), ne);
					}

				}
			}
		});

		
		createActions();
		createColumns(viewer);
		
		viewer.setInput(system);
		system.addRegionListener(regionListener);
		
		if (Activator.getDefault().getPreferenceStore().getBoolean(DevicePreferenceConstants.AUTO_SAVE_REGIONS)) {
			try {
				List<ScanRegion<IROI>> regions = stash.unstash(List.class);
				if (regions!=null && !regions.isEmpty()) {
					for (ScanRegion<IROI> scanRegion : regions) {
						IRegion region = createRegion((RegionType)scanRegion.getType(), system.getPlotName(), scanRegion.getRoi());
						region.setUserObject(scanRegion);
					}
				}
				viewer.refresh();
			} catch (Exception ne) {
				logger.error("Unable to read stored regions!", ne);
			}
		}

	}
	
	@Override
    public void saveState(IMemento memento) {
    	super.saveState(memento);
    	
    	if (!Activator.getDefault().getPreferenceStore().getBoolean(DevicePreferenceConstants.AUTO_SAVE_REGIONS)) return;
    	try {
    		stash.stash(ScanRegionProvider.getScanRegions(system));
		} catch (Exception e) {
			logger.error("Problem stashing control factory!", e);
		}
    }


	private IPlottingSystem<?> getPlottingSystem() {
        // We search for a view which has a PlottingController attached to its plotting system
		// Then we know that this view is designed to respond to mapping.
		IViewPart map = PlotUtil.getRegionView();
		return map.getAdapter(IPlottingSystem.class);
	}

	private void createActions() {

		IToolBarManager toolBarMan = getViewSite().getActionBars().getToolBarManager();
		IMenuManager    menuMan    = getViewSite().getActionBars().getMenuManager();
		MenuManager     rightClick     = new MenuManager();
		List<IContributionManager> mans = Arrays.asList(toolBarMan, menuMan, rightClick);
				
		addGroups("add", mans, createRegionActions());

		IAction autoSave = new Action("Automatically save regions", IAction.AS_CHECK_BOX) {
			public void run() {
				Activator.getDefault().getPreferenceStore().setValue(DevicePreferenceConstants.AUTO_SAVE_REGIONS, isChecked());
			}
		};
		autoSave.setChecked(Activator.getDefault().getPreferenceStore().getBoolean(DevicePreferenceConstants.AUTO_SAVE_REGIONS));
		autoSave.setImageDescriptor(Activator.getImageDescriptor("icons/autosave.png"));
		addGroups("auto", mans, autoSave);
	}
	
	private void createColumns(TableViewer viewer) {
		
        TableViewerColumn var   = new TableViewerColumn(viewer, SWT.LEFT, 0);
		var.getColumn().setText("Name");
		var.getColumn().setWidth(200);
		var.setLabelProvider(new ColumnLabelProvider() {
			public String getText(Object element) {
				return ((ScanRegion<IROI>)element).getName();
			}
		});
		
		var   = new TableViewerColumn(viewer, SWT.LEFT, 1);
		var.getColumn().setText("Axes");
		var.getColumn().setWidth(300);
		
		var.setLabelProvider(new ColumnLabelProvider() {
			public String getText(Object element) {
				if (element==null || ((ScanRegion<IROI>)element).getScannables()==null) return "";
				return ((ScanRegion<IROI>)element).getScannables().toString();
			}
		});
	}

	
	@Override
	public void setFocus() {
		if (viewer!=null) viewer.getTable().setFocus();
	}
	
	@Override
	public void dispose() {
		if (viewer!=null) viewer.getTable().dispose();
		if (system!=null) system.removeRegionListener(regionListener);
		super.dispose();
	}

	private void addGroups(String id, List<IContributionManager> managers, IAction... actions) {
		for (IContributionManager man : managers) addGroup(id, man, actions);
	}
	private void addGroup(String id, IContributionManager manager, IAction... actions) {
		manager.add(new Separator(id));
		for (IAction action : actions) {
			manager.add(action);
		}
	}

	private IAction createRegionActions() {
		
		final String regionViewName = PlotUtil.getRegionViewName();
		final ToolTip tip = new ToolTip(viewer.getTable().getShell(), SWT.BALLOON);
        
		MenuAction rois = new MenuAction("Add Region");

		ActionContributionItem menu  = (ActionContributionItem)system.getActionBars().getMenuManager().find(BasePlottingConstants.ADD_REGION);
		IAction        menuAction = (IAction)menu.getAction();	

		for (RegionType regionType : regionTypes) {
			
            IAction action = new Action("Press to click and drag a "+regionType.getName()+" on '"+PlotUtil.getRegionViewName()+"'") {
            	public void run() {
            		createRegion(regionType, regionViewName, null);
    				showTip(tip, "Drag a box in the '"+regionViewName+"' to create a scan region.");
            		rois.setSelectedAction(this);
            	}
            };

			final ImageDescriptor des = findImageDescriptor(menuAction, regionType.getId());
            action.setImageDescriptor(des);
            rois.add(action);
		}
		
		rois.setSelectedAction(rois.getAction(0));
		return rois;
	}

	private ImageDescriptor findImageDescriptor(IAction menuAction, String id) {

		try {
	        final Method method = menuAction.getClass().getMethod("findAction", String.class);
	        IAction paction = (IAction)method.invoke(menuAction, id);
	        return paction.getImageDescriptor();
		} catch (Exception ne) {
			logger.error("Cannot get plotting menu for adding regions!", ne);
			return Activator.getImageDescriptor("icons/ProfileBox.png");
		}
	}

	private IRegion createRegion(RegionType regionType, final String regionViewName, IROI roi) {
		
		if (system!=null) {
			try {
				IRegion region = system.createRegion(RegionUtils.getUniqueName("Scan "+regionType.getName(), system), regionType);
				
				String x = system.getAxes().get(0).getTitle();
				String y = system.getAxes().get(1).getTitle();
				region.setUserObject(new ScanRegion<IROI>(region.getName(), regionType, Arrays.asList(x,y))); 
				region.setRegionColor(ColorConstants.blue);
				region.setAlpha(25);
				region.setLineWidth(1);
				if (roi!=null) {
					region.setROI(roi);
					system.addRegion(region);
					region.repaint();
				}
				return region;

			} catch (Exception e1) {
				logger.error("Cannot create a bounding box!", e1);
			}
		}
		return null;
	}

	private void showTip(ToolTip tip, String message) {
		
		if (tip==null) return;
    	tip.setMessage(message);
		PointerInfo a = MouseInfo.getPointerInfo();
		java.awt.Point loc = a.getLocation();
		
		tip.setLocation(loc.x, loc.y+20);
        tip.setVisible(true);
	}

}
