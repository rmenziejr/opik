import React from "react";
import { Navigate } from "@tanstack/react-router";
import useAppStore from "@/store/AppStore";
import { DEFAULT_WORKSPACE_NAME } from "@/constants/user";
import { useWorkspaceNameFromURL } from "@/hooks/useWorkspaceNameFromURL";
import { useUserInfo } from "@/api/auth/useUserInfo";
import Loader from "@/components/shared/Loader/Loader";

type WorkspacePreloaderProps = {
  children: React.ReactNode;
};

const WorkspacePreloader: React.FunctionComponent<WorkspacePreloaderProps> = ({
  children,
}) => {
  const { data: userInfo, isLoading } = useUserInfo();
  const workspaceNameFromURL = useWorkspaceNameFromURL();

  // Show loader while checking authentication
  if (isLoading) {
    return <Loader />;
  }

  // If not logged in, redirect to login page
  if (userInfo && !userInfo.loggedIn) {
    window.location.href = "/login";
    return null;
  }

  useAppStore.getState().setActiveWorkspaceName(DEFAULT_WORKSPACE_NAME);

  if (workspaceNameFromURL && workspaceNameFromURL !== DEFAULT_WORKSPACE_NAME) {
    return (
      <Navigate
        to="/$workspaceName"
        params={{ workspaceName: DEFAULT_WORKSPACE_NAME }}
      />
    );
  }

  return children;
};

export default WorkspacePreloader;
