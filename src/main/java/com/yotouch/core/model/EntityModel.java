package com.yotouch.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yotouch.core.Consts;

import java.util.Calendar;

/**
 * Created by king on 3/29/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityModel {
    private String uuid;
    private String creatorUuid;
    private Calendar createdAt;
    private Calendar updatedAt;
    private String updaterUuid;
    private int status = Consts.STATUS_NORMAL;
    private String company;

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getCreatorUuid() {
        return creatorUuid;
    }

    public void setCreatorUuid(String creatorUuid) {
        this.creatorUuid = creatorUuid;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }

    public Calendar getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Calendar updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdaterUuid() {
        return updaterUuid;
    }

    public void setUpdaterUuid(String updaterUuid) {
        this.updaterUuid = updaterUuid;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
