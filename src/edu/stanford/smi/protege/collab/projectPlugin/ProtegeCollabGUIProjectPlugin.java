package edu.stanford.smi.protege.collab.projectPlugin;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JSplitPane;

import edu.stanford.bmir.protegex.chao.ChAOKbManager;
import edu.stanford.bmir.protegex.chao.annotation.api.AnnotationFactory;
import edu.stanford.smi.protege.action.DisplayHtml;
import edu.stanford.smi.protege.collab.annotation.gui.AnnotationsDisplayComponent;
import edu.stanford.smi.protege.collab.annotation.gui.ConfigureCollabProtegeAction;
import edu.stanford.smi.protege.collab.util.HasAnnotationCache;
import edu.stanford.smi.protege.collab.util.UIUtil;
import edu.stanford.smi.protege.model.Cls;
import edu.stanford.smi.protege.model.KnowledgeBase;
import edu.stanford.smi.protege.model.Slot;
import edu.stanford.smi.protege.plugin.ProjectPluginAdapter;
import edu.stanford.smi.protege.ui.ProjectManager;
import edu.stanford.smi.protege.ui.ProjectMenuBar;
import edu.stanford.smi.protege.ui.ProjectToolBar;
import edu.stanford.smi.protege.ui.ProjectView;
import edu.stanford.smi.protege.util.ComponentFactory;
import edu.stanford.smi.protege.util.ComponentUtilities;
import edu.stanford.smi.protege.util.Log;
import edu.stanford.smi.protege.util.ModalDialog;
import edu.stanford.smi.protege.util.ProjectViewEvent;
import edu.stanford.smi.protege.util.ProjectViewListener;
import edu.stanford.smi.protege.widget.TabWidget;
import edu.stanford.smi.protegex.changes.ui.CreateChAOProjectDialog;


public class ProtegeCollabGUIProjectPlugin extends ProjectPluginAdapter {

	public static String SHOW_COLLAB_PANEL_PRJ_INFO = "show_collaboration_panel";	
	public static String USER_GUIDE_HTML = "http://protegewiki.stanford.edu/index.php/Collaborative_Protege";

	private AnnotationsDisplayComponent annotationsDisplayComponent;
	private ProjectViewListener projectViewListener;
	private JMenu collabMenu;
	private Action enableCollabPanelAction;
	private JCheckBoxMenuItem enableCollabPanelCheckBox;

	@Override
	public void afterShow(ProjectView view, ProjectToolBar toolBar, ProjectMenuBar menuBar) {
		KnowledgeBase kb = view.getProject().getKnowledgeBase();

		insertCollabMenu(kb, menuBar);		
		if (!UIUtil.isCollaborationPanelEnabled(kb.getProject())) { return ; }
		enableCollaborationPanel(kb);				
	}


	private boolean ensureChAOKBLoaded(KnowledgeBase kb) {
		KnowledgeBase chaoKB = ChAOKbManager.getChAOKb(kb);
		if (chaoKB != null) {return true;}

		if (kb.getProject().isMultiUserClient()) {
			ModalDialog.showMessageDialog(ProjectManager.getProjectManager().getCurrentProjectView(),
					"The Collaboration panel could not find the annotation/changes knowledge base (ChAO KB) \n" +
					"associated to this project. One possible reason is that the\n" +
					"annotations/changes knowledge base was not configured on the server.\n" +
					"Please check the configuration of the project on the server side.\n" +
					"The Collaboration panel will not work at the current time.",
					"No annotation/changes knowledge base", ModalDialog.MODE_CLOSE);
			return false;
		}

		CreateChAOProjectDialog dialog = new CreateChAOProjectDialog(kb);
		dialog.showDialog();
		chaoKB = dialog.getChangesKb();

		if (chaoKB == null) {
			ModalDialog.showMessageDialog(ProjectManager.getProjectManager().getCurrentProjectView(),
					"Could not find or create the changes and annotations\n" +
					"ontology. The collaboration panel will not work in this session.", "No ChAO");
			return false;
		}

		chaoKB = ChAOKbManager.getChAOKb(kb);
		return chaoKB != null;
	}


	private void backwardCompatibilityFix(KnowledgeBase kb) {
		//add subject if not added as a template slot of annotation
		KnowledgeBase chaoKb = ChAOKbManager.getChAOKb(kb);
		if (chaoKb == null) { return; }
		try {
			AnnotationFactory factory = new AnnotationFactory(chaoKb);
			Cls annotationCls = factory.getAnnotationClass();
			Slot subjectSlot = factory.getSubjectSlot();
			if (!annotationCls.hasTemplateSlot(subjectSlot)) {
				annotationCls.addDirectTemplateSlot(subjectSlot);
				Log.getLogger().info("Backwards compatibility fix for the Changes and Annotation ontology (done)");
			}
		} catch (Exception e) {
			Log.getLogger().log(Level.WARNING, "Failed to make the backwards compatibility fix for the ChAO Kb", e);
		}
	}


	private void enableCollaborationPanel(KnowledgeBase kb) {
		Log.getLogger().info("Started Collaborative Protege on " + new Date());

		boolean success = ensureChAOKBLoaded(kb);
		if (!success) {
			enableCollabPanelCheckBox.setSelected(false);
			return ;
		}

		backwardCompatibilityFix(kb);

		ProjectView view = ProjectManager.getProjectManager().getCurrentProjectView();
		if (view != null) {			
			insertCollabPanel(view);
			attachProjectViewListener(view);

			UIUtil.adjustTreeFrameRenderers(view);
			UIUtil.adjustAnnotationBrowserPattern(kb);
		}
	}


	private void disposeCollaborationPanel(KnowledgeBase kb) {
		if (annotationsDisplayComponent == null) {	return; }

		ProjectView view = ProjectManager.getProjectManager().getCurrentProjectView();

		//detach project view listener if present
		if (view != null && projectViewListener != null) {
			view.removeProjectViewListener(projectViewListener);
		}
		HasAnnotationCache.clearCache();

		JComponent splitPane = (JComponent) annotationsDisplayComponent.getParent();
		JComponent parent = null;
		if (splitPane != null) {
			parent = (JComponent)splitPane.getParent();			
			if (parent != null) {
				parent.remove(splitPane);
				parent.add(view, BorderLayout.CENTER);
				parent.revalidate();
			}
		}		

		//remove the annotations components
		if (annotationsDisplayComponent != null) {
			ComponentUtilities.dispose(annotationsDisplayComponent);
			annotationsDisplayComponent = null;
		}
	}

	private void disableCollaborationPanel(KnowledgeBase kb) {
		Log.getLogger().info("Stopped Collaborative Protege on " + new Date());
		JComponent mainPanel = ProjectManager.getProjectManager().getMainPanel();
		mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		disposeCollaborationPanel(kb);
		ProjectManager.getProjectManager().reloadUI(true);
		
		mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}


	private void attachProjectViewListener(ProjectView view) {
		projectViewListener = new ProjectViewListener() {
			public void closed(ProjectViewEvent event) {}
			public void saved(ProjectViewEvent event) {}
			public void tabAdded(ProjectViewEvent event) {
				UIUtil.adjustTreeFrameRenderer((TabWidget)event.getWidget());
				annotationsDisplayComponent.init();
			}
		};
		view.addProjectViewListener(projectViewListener);
	}

	private void insertCollabMenu(KnowledgeBase kb, ProjectMenuBar menuBar) {
		collabMenu = new JMenu("Collaboration");
		enableCollabPanelCheckBox = new JCheckBoxMenuItem(getEnableCollabPanelAction(kb));
		enableCollabPanelCheckBox.setSelected(UIUtil.isCollaborationPanelEnabled(kb.getProject()));
		collabMenu.add(enableCollabPanelCheckBox);
		ComponentFactory.addMenuItemNoIcon(collabMenu, new DisplayHtml("Collaboration User's Guide", USER_GUIDE_HTML));
		collabMenu.addSeparator();
		collabMenu.add(new JMenuItem(new ConfigureCollabProtegeAction(kb, this))); //kind of funky		
		menuBar.add(collabMenu);
	}


	private Action getEnableCollabPanelAction(final KnowledgeBase kb) {
		if (enableCollabPanelAction == null) {
			enableCollabPanelAction = new AbstractAction("Show Collaboration Panel") {
				public void actionPerformed(ActionEvent arg0) {
					boolean toEnable = enableCollabPanelCheckBox.isSelected();
					UIUtil.setCollaborationPanelEnabled(kb.getProject(), toEnable);
					if (toEnable) {
						enableCollaborationPanel(kb); 
					} else { 
						disableCollaborationPanel(kb);
					}
				}
			};
		}
		return enableCollabPanelAction;
	}


	private AnnotationsDisplayComponent insertCollabPanel(ProjectView view) {
		JComponent parent = (JComponent)view.getParent();
		parent.remove(view);

		final JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);	
		pane.setResizeWeight(0.50);
		pane.setDividerLocation(0.75);
		pane.setOneTouchExpandable(true);
		pane.setLeftComponent(view);
		annotationsDisplayComponent = new AnnotationsDisplayComponent(view.getProject().getKnowledgeBase());
		pane.setRightComponent(annotationsDisplayComponent);
		annotationsDisplayComponent.setMinimumSize(new Dimension(0,0));
		
		parent.add(pane, BorderLayout.CENTER);
		parent.revalidate();
		parent.repaint();		
		
		return annotationsDisplayComponent;
	}


	@Override
	public void beforeHide(ProjectView view, ProjectToolBar toolBar, ProjectMenuBar menuBar) {
		JMenuBar mainMenuBar = ProjectManager.getProjectManager().getCurrentProjectMenuBar();
		enableCollabPanelAction = null;
		enableCollabPanelCheckBox = null;
		mainMenuBar.remove(collabMenu);
		collabMenu = null;
		disposeCollaborationPanel(view.getProject().getKnowledgeBase());		
	}

	
	public AnnotationsDisplayComponent getAnnotationsDisplayComponent() {
		return annotationsDisplayComponent;
	}

}
